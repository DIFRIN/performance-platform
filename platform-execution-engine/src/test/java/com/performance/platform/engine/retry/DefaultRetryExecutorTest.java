package com.performance.platform.engine.retry;

import com.performance.platform.domain.execution.RetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultRetryExecutor")
class DefaultRetryExecutorTest {

    private final DefaultRetryExecutor executor = new DefaultRetryExecutor();

    @Nested
    @DisplayName("Success scenarios")
    class SuccessScenarios {

        @Test
        @DisplayName("Should return result on first attempt")
        void shouldReturnResultOnFirstAttempt() {
            RetryPolicy policy = RetryPolicy.defaults();

            String result = executor.executeWithRetry(policy, () -> "ok");

            assertEquals("ok", result);
        }

        @Test
        @DisplayName("Should succeed after transient failures")
        void shouldSucceedAfterTransientFailures() {
            RetryPolicy policy = RetryPolicy.defaults();
            AtomicInteger attempts = new AtomicInteger(0);

            String result = executor.executeWithRetry(policy, () -> {
                int current = attempts.incrementAndGet();
                if (current < 3) {
                    throw new RuntimeException("Transient failure #" + current);
                }
                return "success-after-" + current;
            });

            assertEquals("success-after-3", result);
            assertEquals(3, attempts.get());
        }

        @Test
        @DisplayName("Should succeed with custom maxAttempts")
        void shouldSucceedWithCustomMaxAttempts() {
            RetryPolicy policy = new RetryPolicy(5, Duration.ofMillis(10), 2.0, Duration.ofSeconds(10), Set.of());
            AtomicInteger attempts = new AtomicInteger(0);

            String result = executor.executeWithRetry(policy, () -> {
                int current = attempts.incrementAndGet();
                if (current < 4) {
                    throw new RuntimeException("Fail #" + current);
                }
                return "ok-" + current;
            });

            assertEquals("ok-4", result);
            assertEquals(4, attempts.get());
        }
    }

    @Nested
    @DisplayName("Failure scenarios")
    class FailureScenarios {

        @Test
        @DisplayName("Should propagate last exception when maxAttempts exhausted")
        void shouldPropagateLastExceptionWhenMaxAttemptsExhausted() {
            RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(10), 2.0, Duration.ofSeconds(10), Set.of());
            String expectedMessage = "Persistent failure";

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> executor.executeWithRetry(policy, () -> {
                        throw new RuntimeException(expectedMessage);
                    }));

            assertEquals(expectedMessage, exception.getMessage());
        }

        @Test
        @DisplayName("Should execute exactly maxAttempts times")
        void shouldExecuteExactlyMaxAttemptsTimes() {
            RetryPolicy policy = new RetryPolicy(4, Duration.ofMillis(10), 2.0, Duration.ofSeconds(10), Set.of());
            AtomicInteger attemptCount = new AtomicInteger(0);

            assertThrows(RuntimeException.class,
                    () -> executor.executeWithRetry(policy, () -> {
                        attemptCount.incrementAndGet();
                        throw new RuntimeException("Fail");
                    }));

            assertEquals(4, attemptCount.get());
        }

        @Test
        @DisplayName("Should not retry on non-retryable exception")
        void shouldNotRetryOnNonRetryableException() {
            Set<Class<? extends Exception>> retryable = Set.of(IllegalStateException.class);
            RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(10), 2.0, Duration.ofSeconds(10), retryable);

            String expectedMessage = "Non-retryable error";
            AtomicInteger attemptCount = new AtomicInteger(0);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> executor.executeWithRetry(policy, () -> {
                        attemptCount.incrementAndGet();
                        throw new IllegalArgumentException(expectedMessage);
                    }));

            assertEquals(expectedMessage, exception.getMessage());
            assertEquals(1, attemptCount.get(), "Should only have attempted once for non-retryable exception");
        }

        @Test
        @DisplayName("Should retry on matching retryable exception")
        void shouldRetryOnMatchingRetryableException() {
            Set<Class<? extends Exception>> retryable = Set.of(IllegalStateException.class);
            RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(10), 2.0, Duration.ofSeconds(10), retryable);
            AtomicInteger attempts = new AtomicInteger(0);

            assertThrows(IllegalStateException.class,
                    () -> executor.executeWithRetry(policy, () -> {
                        attempts.incrementAndGet();
                        throw new IllegalStateException("State error");
                    }));

            assertEquals(3, attempts.get(), "Should retry on matching exception type");
        }

        @Test
        @DisplayName("Should retry on subclass of retryable exception")
        void shouldRetryOnSubclassOfRetryableException() {
            Set<Class<? extends Exception>> retryable = Set.of(RuntimeException.class);
            RetryPolicy policy = new RetryPolicy(2, Duration.ofMillis(10), 2.0, Duration.ofSeconds(10), retryable);
            AtomicInteger attempts = new AtomicInteger(0);

            assertThrows(IllegalArgumentException.class,
                    () -> executor.executeWithRetry(policy, () -> {
                        attempts.incrementAndGet();
                        throw new IllegalArgumentException("subclass error");
                    }));

            assertEquals(2, attempts.get(), "Should retry on subclass of retryable exception");
        }

        @Test
        @DisplayName("Should treat empty retryableExceptions as retry-all")
        void shouldTreatEmptyRetryableExceptionsAsRetryAll() {
            RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(10), 2.0, Duration.ofSeconds(10), Set.of());
            AtomicInteger attempts = new AtomicInteger(0);

            assertThrows(IllegalArgumentException.class,
                    () -> executor.executeWithRetry(policy, () -> {
                        attempts.incrementAndGet();
                        throw new IllegalArgumentException("Any error");
                    }));

            assertEquals(3, attempts.get(), "Empty set means all exceptions are retryable");
        }
    }

    @Nested
    @DisplayName("Delay calculation")
    class DelayCalculation {

        @Test
        @DisplayName("Should compute correct delay with default policy (multiplier=2.0)")
        void shouldComputeCorrectDelayWithDefaultPolicy() {
            RetryPolicy policy = RetryPolicy.defaults(); // initialDelay=1s, multiplier=2.0, maxDelay=30s

            // Before attempt 1: initialDelay * 2^0 = 1000ms
            assertEquals(1000L, executor.computeDelayMs(policy, 1));
            // Before attempt 2: initialDelay * 2^1 = 2000ms
            assertEquals(2000L, executor.computeDelayMs(policy, 2));
            // Before attempt 3: initialDelay * 2^2 = 4000ms
            assertEquals(4000L, executor.computeDelayMs(policy, 3));
            // Before attempt 4: initialDelay * 2^3 = 8000ms
            assertEquals(8000L, executor.computeDelayMs(policy, 4));
            // Before attempt 5: initialDelay * 2^4 = 16000ms
            assertEquals(16000L, executor.computeDelayMs(policy, 5));
            // Before attempt 6: initialDelay * 2^5 = 32000ms → capped to 30000ms
            assertEquals(30000L, executor.computeDelayMs(policy, 6));
        }

        @Test
        @DisplayName("Should cap delay at maxDelay")
        void shouldCapDelayAtMaxDelay() {
            RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(100), 10.0, Duration.ofMillis(500), Set.of());

            // 100 * 10^0 = 100
            assertEquals(100L, executor.computeDelayMs(policy, 1));
            // 100 * 10^1 = 1000 → capped to 500
            assertEquals(500L, executor.computeDelayMs(policy, 2));
            // 100 * 10^2 = 10000 → capped to 500
            assertEquals(500L, executor.computeDelayMs(policy, 3));
        }

        @Test
        @DisplayName("Should compute correct delay with multiplier=1.0 (constant backoff)")
        void shouldComputeConstantDelayWithMultiplierOne() {
            RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(200), 1.0, Duration.ofSeconds(10), Set.of());

            assertEquals(200L, executor.computeDelayMs(policy, 1));
            assertEquals(200L, executor.computeDelayMs(policy, 2));
            assertEquals(200L, executor.computeDelayMs(policy, 3));
        }

        @Test
        @DisplayName("Should compute correct delay with multiplier=3.0")
        void shouldComputeCorrectDelayWithMultiplierThree() {
            RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(50), 3.0, Duration.ofSeconds(10), Set.of());

            // 50 * 3^0 = 50
            assertEquals(50L, executor.computeDelayMs(policy, 1));
            // 50 * 3^1 = 150
            assertEquals(150L, executor.computeDelayMs(policy, 2));
            // 50 * 3^2 = 450
            assertEquals(450L, executor.computeDelayMs(policy, 3));
        }
    }

    @Nested
    @DisplayName("Elapsed time")
    class ElapsedTime {

        @Test
        @DisplayName("Total elapsed time should be approximately sum of backoff delays")
        void totalElapsedTimeShouldBeApproximatelySumOfBackoffDelays() {
            // maxAttempts=3, initialDelay=50ms, multiplier=2.0, maxDelay=200ms
            // Expected sleeps: 50ms + 100ms = 150ms total
            RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(50), 2.0, Duration.ofMillis(200), Set.of());
            AtomicInteger attempts = new AtomicInteger(0);

            long start = System.currentTimeMillis();

            assertThrows(RuntimeException.class,
                    () -> executor.executeWithRetry(policy, () -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("fail");
                    }));

            long elapsed = System.currentTimeMillis() - start;

            assertEquals(3, attempts.get());
            // Expected: ~150ms of sleep + execution overhead
            // Allow generous tolerance for CI/OS scheduling variance
            assertTrue(elapsed >= 130, "Elapsed time " + elapsed + "ms should be >= 130ms (expected ~150ms sleeps)");
            assertTrue(elapsed < 500, "Elapsed time " + elapsed + "ms should be < 500ms (expected ~150ms sleeps, allowing tolerance)");
        }

        @Test
        @DisplayName("First-attempt success should have negligible elapsed time")
        void firstAttemptSuccessShouldHaveNegligibleElapsedTime() {
            RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(1000), 2.0, Duration.ofSeconds(30), Set.of());

            long start = System.currentTimeMillis();
            String result = executor.executeWithRetry(policy, () -> "instant");
            long elapsed = System.currentTimeMillis() - start;

            assertEquals("instant", result);
            assertTrue(elapsed < 100, "First-attempt success should have negligible delay, got " + elapsed + "ms");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should work with maxAttempts=1 (no retry)")
        void shouldWorkWithMaxAttemptsOne() {
            RetryPolicy policy = new RetryPolicy(1, Duration.ofMillis(100), 2.0, Duration.ofSeconds(10), Set.of());

            // Success case
            String result = executor.executeWithRetry(policy, () -> "ok");
            assertEquals("ok", result);

            // Failure case — should fail immediately without sleep
            long start = System.currentTimeMillis();
            assertThrows(RuntimeException.class,
                    () -> executor.executeWithRetry(policy, () -> {
                        throw new RuntimeException("fail");
                    }));
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 100, "maxAttempts=1 should not sleep, elapsed=" + elapsed + "ms");
        }
    }
}
