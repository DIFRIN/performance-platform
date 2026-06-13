package com.performance.platform.domain.injection;

import com.performance.platform.domain.id.TaskId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour InjectionResult — construction, validations, computeErrorRate(), immuabilite rawStats.
 */
@DisplayName("InjectionResult")
class InjectionResultTest {

    private static TaskId taskId() {
        return TaskId.of("inject-1");
    }

    private static Path reportDir() {
        return Path.of("/tmp/gatling/reports/sim-001");
    }

    private static InjectionResult validResult() {
        return new InjectionResult(
            taskId(),
            "com.performance.Simulation",
            Duration.ofSeconds(30),
            1000L,
            980L,
            20L,
            2.0,
            33.3,
            45L,    // p50
            78L,    // p75
            120L,   // p90
            145L,   // p95
            180L,   // p99
            250L,   // max
            12L,    // min
            42.5,   // meanMs
            reportDir(),
            Map.of("generator", "gatling")
        );
    }

    // ════════════════════════════════════════════════════════════════════
    // Construction nominale
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction nominale")
    class ConstructionNominale {

        @Test
        @DisplayName("tous les champs sont preserves via les getters")
        void allFieldsPreserved() {
            var result = validResult();

            assertEquals(taskId(), result.taskId());
            assertEquals("com.performance.Simulation", result.simulationClass());
            assertEquals(Duration.ofSeconds(30), result.duration());
            assertEquals(1000L, result.totalRequests());
            assertEquals(980L, result.successfulRequests());
            assertEquals(20L, result.failedRequests());
            assertEquals(2.0, result.errorRate(), 0.0001);
            assertEquals(33.3, result.throughput(), 0.0001);
            assertEquals(45L, result.p50Ms());
            assertEquals(78L, result.p75Ms());
            assertEquals(120L, result.p90Ms());
            assertEquals(145L, result.p95Ms());
            assertEquals(180L, result.p99Ms());
            assertEquals(250L, result.maxMs());
            assertEquals(12L, result.minMs());
            assertEquals(42.5, result.meanMs(), 0.0001);
            assertEquals(reportDir(), result.gatlingReportDirectory());
            assertEquals(Map.of("generator", "gatling"), result.rawStats());
        }

        @Test
        @DisplayName("rawStats null devient Map vide")
        void nullRawStatsBecomesEmptyMap() {
            var result = new InjectionResult(
                taskId(), "sim", Duration.ofSeconds(1),
                100, 90, 10, 10.0, 10.0,
                10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                reportDir(), null
            );

            assertEquals(Map.of(), result.rawStats());
        }

        @Test
        @DisplayName("valeurs limites : errorRate = 0.0 autorise")
        void errorRateZeroAllowed() {
            var result = new InjectionResult(
                taskId(), "sim", Duration.ofSeconds(1),
                100, 100, 0, 0.0, 10.0,
                10L, 20L, 30L, 40L, 50L, 60L, 5L, 5.0,
                reportDir(), Map.of()
            );

            assertEquals(0.0, result.errorRate(), 0.0001);
        }

        @Test
        @DisplayName("valeurs limites : errorRate = 100.0 autorise")
        void errorRateHundredAllowed() {
            var result = new InjectionResult(
                taskId(), "sim", Duration.ofSeconds(1),
                100, 0, 100, 100.0, 10.0,
                10L, 20L, 30L, 40L, 50L, 60L, 5L, 5.0,
                reportDir(), Map.of()
            );

            assertEquals(100.0, result.errorRate(), 0.0001);
        }

        @Test
        @DisplayName("valeurs limites : totalRequests = 0 autorise")
        void totalRequestsZeroAllowed() {
            var result = new InjectionResult(
                taskId(), "sim", Duration.ofSeconds(1),
                0, 0, 0, 0.0, 0.0,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0,
                reportDir(), Map.of()
            );

            assertEquals(0L, result.totalRequests());
        }

        @Test
        @DisplayName("valeurs limites : throughput = 0.0 autorise")
        void throughputZeroAllowed() {
            var result = new InjectionResult(
                taskId(), "sim", Duration.ofSeconds(1),
                100, 90, 10, 10.0, 0.0,
                10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                reportDir(), Map.of()
            );

            assertEquals(0.0, result.throughput(), 0.0001);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Validation non-null
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validation non-null")
    class ValidationNonNull {

        @Test
        @DisplayName("taskId null leve NullPointerException")
        void nullTaskIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new InjectionResult(
                    null, "sim", Duration.ofSeconds(1),
                    100, 90, 10, 10.0, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
        }

        @Test
        @DisplayName("simulationClass null leve NullPointerException")
        void nullSimulationClassThrows() {
            assertThrows(NullPointerException.class, () ->
                new InjectionResult(
                    taskId(), null, Duration.ofSeconds(1),
                    100, 90, 10, 10.0, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
        }

        @Test
        @DisplayName("duration null leve NullPointerException")
        void nullDurationThrows() {
            assertThrows(NullPointerException.class, () ->
                new InjectionResult(
                    taskId(), "sim", null,
                    100, 90, 10, 10.0, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
        }

        @Test
        @DisplayName("gatlingReportDirectory null leve NullPointerException")
        void nullReportDirThrows() {
            assertThrows(NullPointerException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, 90, 10, 10.0, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    null, Map.of()
                )
            );
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Validation des contraintes metier
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validation des contraintes metier")
    class ValidationContraintes {

        @Test
        @DisplayName("errorRate < 0.0 leve IllegalArgumentException")
        void errorRateNegativeThrows() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, 90, 10, -0.1, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("errorRate"));
        }

        @Test
        @DisplayName("errorRate > 100.0 leve IllegalArgumentException")
        void errorRateAbove100Throws() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, 90, 10, 100.1, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("errorRate"));
        }

        @Test
        @DisplayName("totalRequests negatif leve IllegalArgumentException")
        void totalRequestsNegativeThrows() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    -1, 90, 10, 10.0, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("totalRequests"));
        }

        @Test
        @DisplayName("successfulRequests negatif leve IllegalArgumentException")
        void successfulRequestsNegativeThrows() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, -1, 10, 10.0, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("successfulRequests"));
        }

        @Test
        @DisplayName("failedRequests negatif leve IllegalArgumentException")
        void failedRequestsNegativeThrows() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, 90, -1, 10.0, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("failedRequests"));
        }

        @Test
        @DisplayName("throughput negatif leve IllegalArgumentException")
        void throughputNegativeThrows() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, 90, 10, 10.0, -0.1,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("throughput"));
        }

        @Test
        @DisplayName("p50Ms negatif leve IllegalArgumentException (latency percentiles)")
        void p50NegativeThrows() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, 90, 10, 10.0, 10.0,
                    -1L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("latency percentiles"));
        }

        @Test
        @DisplayName("p99Ms negatif leve IllegalArgumentException (latency percentiles)")
        void p99NegativeThrows() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, 90, 10, 10.0, 10.0,
                    10L, 20L, 30L, 40L, -1L, 60L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("latency percentiles"));
        }

        @Test
        @DisplayName("maxMs negatif leve IllegalArgumentException (latency percentiles)")
        void maxNegativeThrows() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, 90, 10, 10.0, 10.0,
                    10L, 20L, 30L, 40L, 50L, -1L, 5L, 15.0,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("latency percentiles"));
        }

        @Test
        @DisplayName("minMs negatif leve IllegalArgumentException (latency percentiles)")
        void minNegativeThrows() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, 90, 10, 10.0, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, -1L, 15.0,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("latency percentiles"));
        }

        @Test
        @DisplayName("meanMs negatif leve IllegalArgumentException")
        void meanMsNegativeThrows() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                new InjectionResult(
                    taskId(), "sim", Duration.ofSeconds(1),
                    100, 90, 10, 10.0, 10.0,
                    10L, 20L, 30L, 40L, 50L, 60L, 5L, -0.1,
                    reportDir(), Map.of()
                )
            );
            assertTrue(ex.getMessage().contains("meanMs"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // computeErrorRate()
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("computeErrorRate()")
    class ComputeErrorRate {

        @Test
        @DisplayName("cas nominal : failed=3, total=10 → 30.0")
        void nominalComputation() {
            double rate = InjectionResult.computeErrorRate(10, 3);
            assertEquals(30.0, rate, 0.0001);
        }

        @Test
        @DisplayName("total=0 retourne 0.0")
        void totalZeroReturnsZero() {
            double rate = InjectionResult.computeErrorRate(0, 0);
            assertEquals(0.0, rate, 0.0001);
        }

        @Test
        @DisplayName("calcul : failed / total * 100")
        void formulaVerification() {
            assertEquals(0.0, InjectionResult.computeErrorRate(100, 0), 0.0001);
            assertEquals(50.0, InjectionResult.computeErrorRate(200, 100), 0.0001);
            assertEquals(100.0, InjectionResult.computeErrorRate(50, 50), 0.0001);
            assertEquals(1.0, InjectionResult.computeErrorRate(100, 1), 0.0001);
            assertEquals(99.0, InjectionResult.computeErrorRate(100, 99), 0.0001);
        }

        @Test
        @DisplayName("methode statique accessible sans instance")
        void staticMethodAccessible() {
            assertDoesNotThrow(() -> InjectionResult.computeErrorRate(10, 2));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Immuabilite rawStats
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Immuabilite rawStats")
    class ImmutabiliteRawStats {

        @Test
        @DisplayName("modifier la Map source apres construction ne modifie pas le record")
        void defensiveCopyOnConstruction() {
            var mutableStats = new HashMap<String, Object>(Map.of("rps", 1500, "p99", 45));
            var result = new InjectionResult(
                taskId(), "sim", Duration.ofSeconds(10),
                1000, 950, 50, 5.0, 100.0,
                10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                reportDir(), mutableStats
            );

            mutableStats.put("rps", 99999);
            mutableStats.put("injected", "hacked");

            assertEquals(Map.of("rps", 1500, "p99", 45), result.rawStats(),
                "modifying source map must not affect record");
        }

        @Test
        @DisplayName("rawStats() retourne une map non-modifiable")
        void getterReturnsUnmodifiableMap() {
            var result = validResult();

            assertThrows(UnsupportedOperationException.class,
                () -> result.rawStats().put("new", "val"));
        }

        @Test
        @DisplayName("rawStats vide : getter retourne une map vide non-modifiable")
        void emptyRawStatsIsUnmodifiable() {
            var result = new InjectionResult(
                taskId(), "sim", Duration.ofSeconds(1),
                100, 90, 10, 10.0, 10.0,
                10L, 20L, 30L, 40L, 50L, 60L, 5L, 15.0,
                reportDir(), null
            );

            assertEquals(Map.of(), result.rawStats());
            assertThrows(UnsupportedOperationException.class,
                () -> result.rawStats().put("new", "val"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // equals / hashCode / toString
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("equals / hashCode / toString")
    class EqualsHashCodeToString {

        @Test
        @DisplayName("deux resultats identiques sont egaux")
        void identicalResultsEqual() {
            var r1 = validResult();
            var r2 = validResult();

            assertEquals(r1, r2);
            assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("taskId different → pas egaux")
        void differentTaskIdNotEqual() {
            var r1 = validResult();
            var r2 = new InjectionResult(
                TaskId.of("other-task"), "com.performance.Simulation",
                Duration.ofSeconds(30), 1000, 980, 20, 2.0, 33.3,
                45L, 78L, 120L, 145L, 180L, 250L, 12L, 42.5,
                reportDir(), Map.of("generator", "gatling")
            );

            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("errorRate different → pas egaux")
        void differentErrorRateNotEqual() {
            var r1 = validResult(); // errorRate = 2.0
            var r2 = new InjectionResult(
                taskId(), "com.performance.Simulation",
                Duration.ofSeconds(30), 1000, 980, 20, 5.0, 33.3,
                45L, 78L, 120L, 145L, 180L, 250L, 12L, 42.5,
                reportDir(), Map.of("generator", "gatling")
            );

            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("rawStats different → pas egaux")
        void differentRawStatsNotEqual() {
            var r1 = validResult(); // {generator=gatling}
            var r2 = new InjectionResult(
                taskId(), "com.performance.Simulation",
                Duration.ofSeconds(30), 1000, 980, 20, 2.0, 33.3,
                45L, 78L, 120L, 145L, 180L, 250L, 12L, 42.5,
                reportDir(), Map.of("generator", "jmeter")
            );

            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("toString contient le nom du record")
        void toStringContainsRecordName() {
            var result = validResult();
            assertTrue(result.toString().contains("InjectionResult"));
        }

        @Test
        @DisplayName("toString contient la simulationClass")
        void toStringContainsSimulationClass() {
            var result = validResult();
            assertTrue(result.toString().contains("com.performance.Simulation"));
        }

        @Test
        @DisplayName("equals avec null retourne false")
        void equalsWithNullReturnsFalse() {
            assertNotEquals(null, validResult());
        }

        @Test
        @DisplayName("equals avec autre type retourne false")
        void equalsWithOtherTypeReturnsFalse() {
            assertNotEquals("not an InjectionResult", validResult());
        }
    }
}
