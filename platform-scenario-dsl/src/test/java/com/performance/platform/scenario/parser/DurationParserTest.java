package com.performance.platform.scenario.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DurationParser")
class DurationParserTest {

    @Nested
    @DisplayName("Valid formats")
    class ValidFormats {

        @Test
        @DisplayName("parse seconds: '30s' -> Duration.ofSeconds(30)")
        void parseSeconds() {
            assertEquals(Duration.ofSeconds(30), DurationParser.parse("30s"));
        }

        @Test
        @DisplayName("parse minutes: '5m' -> Duration.ofMinutes(5)")
        void parseMinutes() {
            assertEquals(Duration.ofMinutes(5), DurationParser.parse("5m"));
        }

        @Test
        @DisplayName("parse hours: '2h' -> Duration.ofHours(2)")
        void parseHours() {
            assertEquals(Duration.ofHours(2), DurationParser.parse("2h"));
        }

        @Test
        @DisplayName("parse milliseconds: '500ms' -> Duration.ofMillis(500)")
        void parseMilliseconds() {
            assertEquals(Duration.ofMillis(500), DurationParser.parse("500ms"));
        }

        @Test
        @DisplayName("parse zero seconds: '0s' -> Duration.ZERO")
        void parseZeroSeconds() {
            assertEquals(Duration.ZERO, DurationParser.parse("0s"));
        }

        @Test
        @DisplayName("parse large values: '999h' -> Duration.ofHours(999)")
        void parseLargeHours() {
            assertEquals(Duration.ofHours(999), DurationParser.parse("999h"));
        }

        @Test
        @DisplayName("parse with whitespace: ' 30s ' -> Duration.ofSeconds(30)")
        void parseWithWhitespace() {
            assertEquals(Duration.ofSeconds(30), DurationParser.parse(" 30s "));
        }

        @Test
        @DisplayName("parse without space: '30s' (no extra spaces)")
        void parseWithoutSpace() {
            assertEquals(Duration.ofSeconds(30), DurationParser.parse("30s"));
        }
    }

    @Nested
    @DisplayName("Null and blank inputs")
    class NullAndBlank {

        @Test
        @DisplayName("null input returns null")
        void nullReturnsNull() {
            assertNull(DurationParser.parse(null));
        }

        @Test
        @DisplayName("empty string returns null")
        void emptyReturnsNull() {
            assertNull(DurationParser.parse(""));
        }

        @Test
        @DisplayName("blank string returns null")
        void blankReturnsNull() {
            assertNull(DurationParser.parse("   "));
        }
    }

    @Nested
    @DisplayName("Invalid formats")
    class InvalidFormats {

        @Test
        @DisplayName("plain number without unit throws")
        void plainNumberThrows() {
            assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("30"));
        }

        @Test
        @DisplayName("unknown unit '30x' throws")
        void unknownUnitThrows() {
            assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("30x"));
        }

        @Test
        @DisplayName("unit without number 's' throws")
        void unitWithoutNumberThrows() {
            assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("s"));
        }

        @Test
        @DisplayName("negative number '-5s' throws")
        void negativeNumberThrows() {
            assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("-5s"));
        }

        @Test
        @DisplayName("decimal number '1.5s' throws")
        void decimalNumberThrows() {
            assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("1.5s"));
        }

        @Test
        @DisplayName("random text 'abc' throws")
        void randomTextThrows() {
            assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("abc"));
        }

        @Test
        @DisplayName("error message contains input value")
        void errorMessageContainsInput() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DurationParser.parse("xyz")
            );
            assertTrue(ex.getMessage().contains("xyz"));
        }
    }

    @Nested
    @DisplayName("parseOrDefault")
    class ParseOrDefault {

        @Test
        @DisplayName("null input returns default")
        void nullReturnsDefault() {
            Duration defaultDuration = Duration.ofMinutes(5);
            assertEquals(defaultDuration, DurationParser.parseOrDefault(null, defaultDuration));
        }

        @Test
        @DisplayName("blank input returns default")
        void blankReturnsDefault() {
            Duration defaultDuration = Duration.ofMinutes(5);
            assertEquals(defaultDuration, DurationParser.parseOrDefault("", defaultDuration));
        }

        @Test
        @DisplayName("valid input returns parsed value")
        void validReturnsParsed() {
            Duration defaultDuration = Duration.ofMinutes(5);
            assertEquals(Duration.ofSeconds(30), DurationParser.parseOrDefault("30s", defaultDuration));
        }

        @Test
        @DisplayName("invalid input still throws")
        void invalidStillThrows() {
            Duration defaultDuration = Duration.ofMinutes(5);
            assertThrows(IllegalArgumentException.class,
                () -> DurationParser.parseOrDefault("bad", defaultDuration));
        }
    }
}
