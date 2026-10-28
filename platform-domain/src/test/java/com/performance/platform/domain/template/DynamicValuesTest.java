package com.performance.platform.domain.template;

import com.performance.platform.domain.template.DynamicValues.Context;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicValuesTest {

    private final AtomicLong counter = new AtomicLong(41);
    private final Context ctx = new Context("exec-1", "step-1", counter::incrementAndGet);

    @Test
    void indexSupportsArithmeticAndPadding() {
        assertThat(DynamicValues.resolve("device-{index}", ctx)).isEqualTo("device-42");
        assertThat(DynamicValues.resolve("{index+100}", ctx)).isEqualTo("143");
        assertThat(DynamicValues.resolve("{index*2}", ctx)).isEqualTo("88");
        assertThat(DynamicValues.resolve("{index%10}", ctx)).isEqualTo("5");
        assertThat(DynamicValues.resolve("{index:6}", ctx)).isEqualTo("000046");
    }

    @Test
    void randomPlaceholdersStayInBounds() {
        for (int i = 0; i < 50; i++) {
            assertThat(Integer.parseInt(DynamicValues.resolve("{random:5-9}", ctx))).isBetween(5, 9);
        }
        assertThat(DynamicValues.resolve("{randomString:12}", ctx)).matches("[A-Za-z0-9]{12}");
        assertThat(DynamicValues.resolve("{randomHex:6}", ctx)).matches("[0-9a-f]{6}");
        assertThat(DynamicValues.resolve("{uuid}", ctx)).matches("[0-9a-f-]{36}");
        assertThat(DynamicValues.resolve("{choice:a|b|c}", ctx)).isIn("a", "b", "c");
        assertThat(DynamicValues.resolve("{date:yyyy}", ctx)).matches("\\d{4}");
    }

    @Test
    void cycleFollowsIndex() {
        assertThat(DynamicValues.resolve("{cycle:a|b|c}", ctx)).isEqualTo("c"); // index 42 -> (42-1)%3 = 2
        assertThat(DynamicValues.resolve("{cycle:a|b|c}", ctx)).isEqualTo("a"); // index 43
    }

    @Test
    void identifiersAreSubstituted() {
        assertThat(DynamicValues.resolve("{executionId}/{stepId}", ctx)).isEqualTo("exec-1/step-1");
    }

    @Test
    void jsonAndUnknownBracesAreUntouched() {
        String body = "{\"device_id\":\"device-{index}\",\"nested\":{\"a\":1},\"x\":\"{unknown}\"}";
        assertThat(DynamicValues.resolve(body, ctx))
                .isEqualTo("{\"device_id\":\"device-42\",\"nested\":{\"a\":1},\"x\":\"{unknown}\"}");
        assertThat(DynamicValues.isDynamic("{\"a\":1}")).isFalse();
        assertThat(DynamicValues.isDynamic("id-{uuid}")).isTrue();
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThatThrownBy(() -> DynamicValues.resolve("{random:x}", ctx)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DynamicValues.resolve("{index/0}", ctx)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toRegexMatchesResolvedValues() {
        String template = "dev-{index+1}-{randomString:4}-{choice:x|y}-{executionId}";
        String regex = DynamicValues.toRegex(template, ctx);
        assertThat(Pattern.matches(regex, DynamicValues.resolve(template, ctx))).isTrue();
        assertThat(Pattern.matches(regex, "other")).isFalse();
    }
}
