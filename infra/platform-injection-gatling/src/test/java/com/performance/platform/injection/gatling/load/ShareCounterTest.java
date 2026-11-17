package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.execution.GrantShare;
import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Hold;
import com.performance.platform.domain.injection.LoadScope;
import com.performance.platform.domain.injection.WorkloadPlan;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShareCounterTest {

    @Test
    void singleShareCountsFromOne() {
        ShareCounter c = new ShareCounter(GrantShare.single());
        assertThat(List.of(c.next(), c.next(), c.next())).containsExactly(1L, 2L, 3L);
    }

    @Test
    void twoAgentsNeverShareAnIndex() {
        ShareCounter a = new ShareCounter(GrantShare.equal(0, 2));
        ShareCounter b = new ShareCounter(GrantShare.equal(1, 2));
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(seen.add(a.next())).isTrue();
            assertThat(seen.add(b.next())).isTrue();
        }
        assertThat(seen).hasSize(2000);
    }

    @Test
    void seedDependsOnPlanAndIndex() {
        WorkloadPlan plan = plan();
        long s0 = ShareCounter.seed(plan, GrantShare.equal(0, 2));
        long s1 = ShareCounter.seed(plan, GrantShare.equal(1, 2));
        assertThat(s0 ^ s1).isEqualTo(1L);
        assertThat(ShareCounter.seed(plan, GrantShare.equal(0, 2))).isEqualTo(s0);
    }

    private static WorkloadPlan plan() {
        return new WorkloadPlan(ArrivalModel.OPEN, LoadScope.GLOBAL, List.of(new Hold(10.0, Duration.ofSeconds(5), false)));
    }
}
