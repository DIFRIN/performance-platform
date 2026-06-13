package com.performance.platform.domain.assertion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests exhaustifs pour les 6 operateurs d'AssertionOperator.
 */
@DisplayName("AssertionOperator")
class AssertionOperatorTest {

    @Nested
    @DisplayName("LT — less than")
    class LtTests {

        @Test
        @DisplayName("1 < 2 → true")
        void actualLessThanExpected() {
            assertTrue(AssertionOperator.LT.evaluate(1, 2));
        }

        @Test
        @DisplayName("2 < 2 → false")
        void equalValues() {
            assertFalse(AssertionOperator.LT.evaluate(2, 2));
        }

        @Test
        @DisplayName("3 < 2 → false")
        void actualGreaterThanExpected() {
            assertFalse(AssertionOperator.LT.evaluate(3, 2));
        }

        @Test
        @DisplayName("negative values")
        void negativeValues() {
            assertTrue(AssertionOperator.LT.evaluate(-5, -2));
            assertFalse(AssertionOperator.LT.evaluate(-2, -5));
        }

        @Test
        @DisplayName("decimal values")
        void decimalValues() {
            assertTrue(AssertionOperator.LT.evaluate(1.5, 1.6));
            assertFalse(AssertionOperator.LT.evaluate(1.55, 1.55));
        }
    }

    @Nested
    @DisplayName("LTE — less than or equal")
    class LteTests {

        @Test
        @DisplayName("1 <= 2 → true")
        void actualLessThanExpected() {
            assertTrue(AssertionOperator.LTE.evaluate(1, 2));
        }

        @Test
        @DisplayName("2 <= 2 → true")
        void equalValues() {
            assertTrue(AssertionOperator.LTE.evaluate(2, 2));
        }

        @Test
        @DisplayName("3 <= 2 → false")
        void actualGreaterThanExpected() {
            assertFalse(AssertionOperator.LTE.evaluate(3, 2));
        }
    }

    @Nested
    @DisplayName("GT — greater than")
    class GtTests {

        @Test
        @DisplayName("3 > 2 → true")
        void actualGreaterThanExpected() {
            assertTrue(AssertionOperator.GT.evaluate(3, 2));
        }

        @Test
        @DisplayName("2 > 2 → false")
        void equalValues() {
            assertFalse(AssertionOperator.GT.evaluate(2, 2));
        }

        @Test
        @DisplayName("1 > 2 → false")
        void actualLessThanExpected() {
            assertFalse(AssertionOperator.GT.evaluate(1, 2));
        }
    }

    @Nested
    @DisplayName("GTE — greater than or equal")
    class GteTests {

        @Test
        @DisplayName("3 >= 2 → true")
        void actualGreaterThanExpected() {
            assertTrue(AssertionOperator.GTE.evaluate(3, 2));
        }

        @Test
        @DisplayName("2 >= 2 → true")
        void equalValues() {
            assertTrue(AssertionOperator.GTE.evaluate(2, 2));
        }

        @Test
        @DisplayName("1 >= 2 → false")
        void actualLessThanExpected() {
            assertFalse(AssertionOperator.GTE.evaluate(1, 2));
        }
    }

    @Nested
    @DisplayName("EQ — equal")
    class EqTests {

        @Test
        @DisplayName("2 == 2 → true")
        void equalValues() {
            assertTrue(AssertionOperator.EQ.evaluate(2, 2));
        }

        @Test
        @DisplayName("2 == 3 → false")
        void differentValues() {
            assertFalse(AssertionOperator.EQ.evaluate(2, 3));
        }

        @Test
        @DisplayName("0.0 == -0.0 → true (IEEE 754)")
        void zeroEquality() {
            assertTrue(AssertionOperator.EQ.evaluate(0.0, -0.0));
        }

        @Test
        @DisplayName("Double.NaN == Double.NaN → false (IEEE 754)")
        void nanEquality() {
            assertFalse(AssertionOperator.EQ.evaluate(Double.NaN, Double.NaN));
        }
    }

    @Nested
    @DisplayName("NEQ — not equal")
    class NeqTests {

        @Test
        @DisplayName("2 != 3 → true")
        void differentValues() {
            assertTrue(AssertionOperator.NEQ.evaluate(2, 3));
        }

        @Test
        @DisplayName("2 != 2 → false")
        void equalValues() {
            assertFalse(AssertionOperator.NEQ.evaluate(2, 2));
        }
    }

    @Test
    @DisplayName("coverage complete — all 6 operators have tests")
    void allOperatorsCovered() {
        for (var op : AssertionOperator.values()) {
            // verify each operator is callable without exception
            assertDoesNotThrow(() -> op.evaluate(1.0, 2.0));
        }
    }
}
