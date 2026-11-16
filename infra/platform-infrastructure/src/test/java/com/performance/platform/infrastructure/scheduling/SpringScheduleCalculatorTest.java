package com.performance.platform.infrastructure.scheduling;

import com.performance.platform.application.exception.InvalidScheduleException;
import com.performance.platform.application.schedule.ScheduleKind;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SpringScheduleCalculator")
class SpringScheduleCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-11-12T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final SpringScheduleCalculator calculator = new SpringScheduleCalculator(CLOCK);

    @Test
    @DisplayName("empty, now and immediate resolve to an immediate run")
    void shouldResolveImmediate() {
        assertThat(calculator.parse(null).kind()).isEqualTo(ScheduleKind.IMMEDIATE);
        assertThat(calculator.parse("  ").kind()).isEqualTo(ScheduleKind.IMMEDIATE);
        assertThat(calculator.parse("now").kind()).isEqualTo(ScheduleKind.IMMEDIATE);
        assertThat(calculator.parse("immediate").scheduled()).isFalse();
    }

    @Test
    @DisplayName("'in 10m' schedules a one-shot run ten minutes later")
    void shouldResolveRelativeDelay() {
        var spec = calculator.parse("in 10m");

        assertThat(spec.kind()).isEqualTo(ScheduleKind.ONCE);
        assertThat(spec.runAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(calculator.nextRun(spec, NOW)).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    @DisplayName("compound delay 'in 1h30m' sums each token")
    void shouldResolveCompoundDelay() {
        var spec = calculator.parse("in 1h30m");

        assertThat(spec.runAt()).isEqualTo(NOW.plusSeconds(90 * 60));
    }

    @Test
    @DisplayName("an ISO-8601 date-time is accepted as a one-shot schedule")
    void shouldResolveAbsoluteInstant() {
        var spec = calculator.parse("2026-11-13T09:00:00Z");

        assertThat(spec.kind()).isEqualTo(ScheduleKind.ONCE);
        assertThat(spec.runAt()).isEqualTo(Instant.parse("2026-11-13T09:00:00Z"));
    }

    @Test
    @DisplayName("a 5-field cron expression is normalized to 6 fields")
    void shouldResolveFiveFieldCron() {
        var spec = calculator.parse("0 2 * * *");

        assertThat(spec.kind()).isEqualTo(ScheduleKind.CRON);
        assertThat(spec.expression()).isEqualTo("0 0 2 * * *");
    }

    @Test
    @DisplayName("'cron:' prefix and 6-field expressions are accepted")
    void shouldResolvePrefixedCron() {
        assertThat(calculator.parse("cron:0 0 2 * * *").expression()).isEqualTo("0 0 2 * * *");
        assertThat(calculator.parse("0 0 2 * * MON").kind()).isEqualTo(ScheduleKind.CRON);
    }

    @Test
    @DisplayName("next cron occurrence is strictly after the reference instant")
    void shouldComputeNextCronOccurrence() {
        var spec = calculator.parse("cron:0 0 2 * * *");

        assertThat(calculator.nextRun(spec, NOW))
                .isEqualTo(Instant.parse("2026-11-13T02:00:00Z"));
    }

    @Test
    @DisplayName("invalid delay, past instant and broken cron are rejected")
    void shouldRejectInvalidExpressions() {
        assertThatThrownBy(() -> calculator.parse("in soon"))
                .isInstanceOf(InvalidScheduleException.class);
        assertThatThrownBy(() -> calculator.parse("in 0m"))
                .isInstanceOf(InvalidScheduleException.class);
        assertThatThrownBy(() -> calculator.parse("2020-01-01T00:00:00Z"))
                .isInstanceOf(InvalidScheduleException.class)
                .hasMessageContaining("future");
        assertThatThrownBy(() -> calculator.parse("cron:not a cron"))
                .isInstanceOf(InvalidScheduleException.class);
    }
}
