package com.performance.platform.injection.gatling.load;

import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;

import io.gatling.javaapi.core.OpenInjectionStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultLoadModelTranslator")
class DefaultLoadModelTranslatorTest {

    private final DefaultLoadModelTranslator translator = new DefaultLoadModelTranslator();

    // === RAMP ===

    @Nested
    @DisplayName("LoadModelType.RAMP")
    class RampTests {

        @Test
        @DisplayName("should translate RAMP to single rampUsersPerSec injection")
        void shouldTranslateRamp() {
            var model = new LoadModel(LoadModelType.RAMP, Map.of(
                    "from", 1, "to", 50, "durationSeconds", 60));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(1);
            assertThat(steps.getFirst().getClass().getSimpleName())
                    .contains("RampRate");
        }

        @Test
        @DisplayName("should throw on missing from parameter")
        void shouldThrowOnMissingFrom() {
            var model = new LoadModel(LoadModelType.RAMP, Map.of(
                    "to", 50, "durationSeconds", 60));

            assertThatThrownBy(() -> translator.translate(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("from");
        }

        @Test
        @DisplayName("should throw on missing to parameter")
        void shouldThrowOnMissingTo() {
            var model = new LoadModel(LoadModelType.RAMP, Map.of(
                    "from", 1, "durationSeconds", 60));

            assertThatThrownBy(() -> translator.translate(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("to");
        }
    }

    // === CONSTANT ===

    @Nested
    @DisplayName("LoadModelType.CONSTANT")
    class ConstantTests {

        @Test
        @DisplayName("should translate CONSTANT to single constantUsersPerSec injection")
        void shouldTranslateConstant() {
            var model = new LoadModel(LoadModelType.CONSTANT, Map.of(
                    "usersPerSec", 100, "durationSeconds", 300));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(1);
            assertThat(steps.getFirst().getClass().getSimpleName())
                    .contains("ConstantRate");
        }

        @Test
        @DisplayName("should throw on missing usersPerSec")
        void shouldThrowOnMissingUsersPerSec() {
            var model = new LoadModel(LoadModelType.CONSTANT, Map.of(
                    "durationSeconds", 300));

            assertThatThrownBy(() -> translator.translate(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("usersPerSec");
        }
    }

    // === SPIKE ===

    @Nested
    @DisplayName("LoadModelType.SPIKE")
    class SpikeTests {

        @Test
        @DisplayName("should translate SPIKE to ramp + constant return")
        void shouldTranslateSpike() {
            var model = new LoadModel(LoadModelType.SPIKE, Map.of(
                    "base", 10, "spike", 200, "spikeDurationSeconds", 30,
                    "totalDurationSeconds", 90));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(2);
            assertThat(steps.get(0).getClass().getSimpleName()).contains("RampRate");
            assertThat(steps.get(1).getClass().getSimpleName()).contains("ConstantRate");
        }

        @Test
        @DisplayName("should handle spike that takes entire duration")
        void shouldHandleSpikeEntireDuration() {
            var model = new LoadModel(LoadModelType.SPIKE, Map.of(
                    "base", 10, "spike", 200, "spikeDurationSeconds", 60,
                    "totalDurationSeconds", 60));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(2);
        }
    }

    // === STAIR ===

    @Nested
    @DisplayName("LoadModelType.STAIR")
    class StairTests {

        @Test
        @DisplayName("should translate STAIR to incrementUsersPerSec injection")
        void shouldTranslateStair() {
            var model = new LoadModel(LoadModelType.STAIR, Map.of(
                    "startUsersPerSec", 10, "incrementUsersPerSec", 20,
                    "steps", 5, "stepDurationSeconds", 30));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(1);
            assertThat(steps.getFirst()).isNotNull();
        }

        @Test
        @DisplayName("should throw on missing steps parameter")
        void shouldThrowOnMissingSteps() {
            var model = new LoadModel(LoadModelType.STAIR, Map.of(
                    "startUsersPerSec", 10, "incrementUsersPerSec", 20,
                    "stepDurationSeconds", 30));

            assertThatThrownBy(() -> translator.translate(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("steps");
        }
    }

    // === SOAK ===

    @Nested
    @DisplayName("LoadModelType.SOAK")
    class SoakTests {

        @Test
        @DisplayName("should translate SOAK to ramp + constant")
        void shouldTranslateSoak() {
            var model = new LoadModel(LoadModelType.SOAK, Map.of(
                    "usersPerSec", 100, "durationSeconds", 3600,
                    "rampDurationSeconds", 300));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(2);
            assertThat(steps.get(0).getClass().getSimpleName()).contains("RampRate");
            assertThat(steps.get(1).getClass().getSimpleName()).contains("ConstantRate");
        }

        @Test
        @DisplayName("should handle soak with ramp equal to total duration")
        void shouldHandleSoakRampEqualsTotal() {
            var model = new LoadModel(LoadModelType.SOAK, Map.of(
                    "usersPerSec", 100, "durationSeconds", 300,
                    "rampDurationSeconds", 300));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(1);
            assertThat(steps.getFirst().getClass().getSimpleName()).contains("RampRate");
        }
    }

    // === BURST ===

    @Nested
    @DisplayName("LoadModelType.BURST")
    class BurstTests {

        @Test
        @DisplayName("should translate BURST to repeated nothingFor + atOnceUsers")
        void shouldTranslateBurst() {
            var model = new LoadModel(LoadModelType.BURST, Map.of(
                    "users", 50, "bursts", 5, "intervalSeconds", 10));

            List<OpenInjectionStep> steps = translator.translate(model);

            // 5 bursts: atOnceUsers + 4 * (nothingFor + atOnceUsers) = 9 steps
            assertThat(steps).isNotNull().hasSize(9);
            assertThat(steps.getFirst()).isNotNull();
            assertThat(steps.get(1)).isNotNull();
        }

        @Test
        @DisplayName("should handle single burst (no nothingFor)")
        void shouldHandleSingleBurst() {
            var model = new LoadModel(LoadModelType.BURST, Map.of(
                    "users", 50, "bursts", 1, "intervalSeconds", 10));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(1);
            assertThat(steps.getFirst()).isNotNull();
        }

        @Test
        @DisplayName("should throw on missing bursts parameter")
        void shouldThrowOnMissingBursts() {
            var model = new LoadModel(LoadModelType.BURST, Map.of(
                    "users", 50, "intervalSeconds", 10));

            assertThatThrownBy(() -> translator.translate(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bursts");
        }
    }

    // === RAMP_UP_DOWN ===

    @Nested
    @DisplayName("LoadModelType.RAMP_UP_DOWN")
    class RampUpDownTests {

        @Test
        @DisplayName("should translate RAMP_UP_DOWN to rampUp + hold + rampDown")
        void shouldTranslateRampUpDown() {
            var model = new LoadModel(LoadModelType.RAMP_UP_DOWN, Map.of(
                    "from", 10, "to", 100, "rampUpDurationSeconds", 60,
                    "holdDurationSeconds", 120, "rampDownDurationSeconds", 60));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(3);
            assertThat(steps.get(0).getClass().getSimpleName()).contains("RampRate");
            assertThat(steps.get(1).getClass().getSimpleName()).contains("ConstantRate");
            assertThat(steps.get(2).getClass().getSimpleName()).contains("RampRate");
        }

        @Test
        @DisplayName("should handle RAMP_UP_DOWN without hold duration")
        void shouldHandleRampUpDownWithoutHold() {
            var model = new LoadModel(LoadModelType.RAMP_UP_DOWN, Map.of(
                    "from", 10, "to", 100, "rampUpDurationSeconds", 60,
                    "holdDurationSeconds", 0, "rampDownDurationSeconds", 60));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(2);
        }
    }

    // === CUSTOM ===

    @Nested
    @DisplayName("LoadModelType.CUSTOM")
    class CustomTests {

        @Test
        @DisplayName("should translate CUSTOM to linear interpolation ramps")
        void shouldTranslateCustom() {
            var model = new LoadModel(LoadModelType.CUSTOM, Map.of(
                    "points", List.of(
                            Map.of("timeSeconds", 0, "usersPerSec", 10),
                            Map.of("timeSeconds", 60, "usersPerSec", 100),
                            Map.of("timeSeconds", 120, "usersPerSec", 50))));

            List<OpenInjectionStep> steps = translator.translate(model);

            // 3 points → 2 segments
            assertThat(steps).isNotNull().hasSize(2);
            assertThat(steps.get(0).getClass().getSimpleName()).contains("RampRate");
            assertThat(steps.get(1).getClass().getSimpleName()).contains("RampRate");
        }

        @Test
        @DisplayName("should throw on non-monotonic time points")
        void shouldThrowOnNonMonotonicTime() {
            var model = new LoadModel(LoadModelType.CUSTOM, Map.of(
                    "points", List.of(
                            Map.of("timeSeconds", 120, "usersPerSec", 10),
                            Map.of("timeSeconds", 60, "usersPerSec", 100))));

            assertThatThrownBy(() -> translator.translate(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("strictly increasing");
        }

        @Test
        @DisplayName("should throw on missing points parameter")
        void shouldThrowOnMissingPoints() {
            var model = new LoadModel(LoadModelType.CUSTOM, Map.of());

            assertThatThrownBy(() -> translator.translate(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("points");
        }

        @Test
        @DisplayName("should throw on empty points list")
        void shouldThrowOnEmptyPoints() {
            var model = new LoadModel(LoadModelType.CUSTOM, Map.of(
                    "points", List.of()));

            assertThatThrownBy(() -> translator.translate(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("points");
        }

        @Test
        @DisplayName("should throw on single point")
        void shouldThrowOnSinglePoint() {
            var model = new LoadModel(LoadModelType.CUSTOM, Map.of(
                    "points", List.of(
                            Map.of("timeSeconds", 0, "usersPerSec", 10))));

            assertThatThrownBy(() -> translator.translate(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 2 points");
        }
    }

    // === Null checks ===

    @Nested
    @DisplayName("Null checks")
    class NullChecks {

        @Test
        @DisplayName("should throw on null model")
        void shouldThrowOnNullModel() {
            assertThatThrownBy(() -> translator.translate(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("model must not be null");
        }
    }

    // === Parameter validation ===

    @Nested
    @DisplayName("Parameter validation")
    class ParameterValidation {

        @Test
        @DisplayName("should throw when numeric parameter is wrong type")
        void shouldThrowOnWrongParamType() {
            var model = new LoadModel(LoadModelType.CONSTANT, Map.of(
                    "usersPerSec", "not-a-number", "durationSeconds", 300));

            assertThatThrownBy(() -> translator.translate(model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Number");
        }

        @Test
        @DisplayName("should accept integer values as double parameters")
        void shouldAcceptIntegerAsDouble() {
            var model = new LoadModel(LoadModelType.CONSTANT, Map.of(
                    "usersPerSec", 100, "durationSeconds", 300));

            List<OpenInjectionStep> steps = translator.translate(model);

            assertThat(steps).isNotNull().hasSize(1);
        }
    }
}
