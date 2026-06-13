package com.performance.platform.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify that all three plugin annotations have RUNTIME retention
 * and target TYPE, as required by the plugin scanning system.
 */
@DisplayName("Plugin annotations retention and target")
class AnnotationsRetentionTest {

    @Nested
    @DisplayName("@Preparation")
    class PreparationTests {

        @Test
        @DisplayName("should have RUNTIME retention")
        void shouldHaveRuntimeRetention() {
            Retention retention = Preparation.class.getAnnotation(Retention.class);
            assertNotNull(retention, "@Preparation must have @Retention");
            assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                    "@Preparation must have RetentionPolicy.RUNTIME");
        }

        @Test
        @DisplayName("should target TYPE")
        void shouldTargetType() {
            Target target = Preparation.class.getAnnotation(Target.class);
            assertNotNull(target, "@Preparation must have @Target");
            assertEquals(1, target.value().length, "@Preparation must target exactly one element type");
            assertEquals(ElementType.TYPE, target.value()[0],
                    "@Preparation must target ElementType.TYPE");
        }

        @Test
        @DisplayName("should be @Documented")
        void shouldBeDocumented() {
            Documented documented = Preparation.class.getAnnotation(Documented.class);
            assertNotNull(documented, "@Preparation must be @Documented");
        }

        @Test
        @DisplayName("should have required name() method")
        void shouldHaveNameMethod() throws NoSuchMethodException {
            Preparation.class.getDeclaredMethod("name");
        }

        @Test
        @DisplayName("should have version() method with default")
        void shouldHaveVersionMethod() throws NoSuchMethodException {
            Preparation.class.getDeclaredMethod("version");
        }

        @Test
        @DisplayName("should have description() method with default")
        void shouldHaveDescriptionMethod() throws NoSuchMethodException {
            Preparation.class.getDeclaredMethod("description");
        }
    }

    @Nested
    @DisplayName("@Injection")
    class InjectionTests {

        @Test
        @DisplayName("should have RUNTIME retention")
        void shouldHaveRuntimeRetention() {
            Retention retention = Injection.class.getAnnotation(Retention.class);
            assertNotNull(retention, "@Injection must have @Retention");
            assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                    "@Injection must have RetentionPolicy.RUNTIME");
        }

        @Test
        @DisplayName("should target TYPE")
        void shouldTargetType() {
            Target target = Injection.class.getAnnotation(Target.class);
            assertNotNull(target, "@Injection must have @Target");
            assertEquals(1, target.value().length, "@Injection must target exactly one element type");
            assertEquals(ElementType.TYPE, target.value()[0],
                    "@Injection must target ElementType.TYPE");
        }

        @Test
        @DisplayName("should be @Documented")
        void shouldBeDocumented() {
            Documented documented = Injection.class.getAnnotation(Documented.class);
            assertNotNull(documented, "@Injection must be @Documented");
        }

        @Test
        @DisplayName("should have required name() method")
        void shouldHaveNameMethod() throws NoSuchMethodException {
            Injection.class.getDeclaredMethod("name");
        }

        @Test
        @DisplayName("should have version() method with default")
        void shouldHaveVersionMethod() throws NoSuchMethodException {
            Injection.class.getDeclaredMethod("version");
        }

        @Test
        @DisplayName("should have description() method with default")
        void shouldHaveDescriptionMethod() throws NoSuchMethodException {
            Injection.class.getDeclaredMethod("description");
        }
    }

    @Nested
    @DisplayName("@Assertion")
    class AssertionTests {

        @Test
        @DisplayName("should have RUNTIME retention")
        void shouldHaveRuntimeRetention() {
            Retention retention = Assertion.class.getAnnotation(Retention.class);
            assertNotNull(retention, "@Assertion must have @Retention");
            assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                    "@Assertion must have RetentionPolicy.RUNTIME");
        }

        @Test
        @DisplayName("should target TYPE")
        void shouldTargetType() {
            Target target = Assertion.class.getAnnotation(Target.class);
            assertNotNull(target, "@Assertion must have @Target");
            assertEquals(1, target.value().length, "@Assertion must target exactly one element type");
            assertEquals(ElementType.TYPE, target.value()[0],
                    "@Assertion must target ElementType.TYPE");
        }

        @Test
        @DisplayName("should be @Documented")
        void shouldBeDocumented() {
            Documented documented = Assertion.class.getAnnotation(Documented.class);
            assertNotNull(documented, "@Assertion must be @Documented");
        }

        @Test
        @DisplayName("should have required name() method")
        void shouldHaveNameMethod() throws NoSuchMethodException {
            Assertion.class.getDeclaredMethod("name");
        }

        @Test
        @DisplayName("should have version() method with default")
        void shouldHaveVersionMethod() throws NoSuchMethodException {
            Assertion.class.getDeclaredMethod("version");
        }

        @Test
        @DisplayName("should have description() method with default")
        void shouldHaveDescriptionMethod() throws NoSuchMethodException {
            Assertion.class.getDeclaredMethod("description");
        }
    }

    @Nested
    @DisplayName("Cross-annotation consistency")
    class CrossConsistencyTests {

        @Test
        @DisplayName("all three annotations should be distinct types")
        void shouldBeDistinctTypes() {
            assertTrue(Preparation.class.isAnnotation(), "Preparation must be an annotation");
            assertTrue(Injection.class.isAnnotation(), "Injection must be an annotation");
            assertTrue(Assertion.class.isAnnotation(), "Assertion must be an annotation");
        }

        @Test
        @DisplayName("no annotation should extend another")
        void shouldNotExtendEachOther() {
            // Annotations are @interface types (Java interfaces underneath);
            // getSuperclass() returns null for interfaces, so use isAssignableFrom
            assertTrue(!Preparation.class.isAssignableFrom(Injection.class),
                    "Injection must not be a subtype of Preparation");
            assertTrue(!Preparation.class.isAssignableFrom(Assertion.class),
                    "Assertion must not be a subtype of Preparation");
            assertTrue(!Injection.class.isAssignableFrom(Preparation.class),
                    "Preparation must not be a subtype of Injection");
            assertTrue(!Injection.class.isAssignableFrom(Assertion.class),
                    "Assertion must not be a subtype of Injection");
            assertTrue(!Assertion.class.isAssignableFrom(Preparation.class),
                    "Preparation must not be a subtype of Assertion");
            assertTrue(!Assertion.class.isAssignableFrom(Injection.class),
                    "Injection must not be a subtype of Assertion");
        }
    }

    /**
     * Smoke test: verify an annotated dummy class compiles and annotations are visible at runtime.
     */
    @Test
    @DisplayName("annotation values should be readable at runtime via reflection")
    void shouldReadAnnotationValuesAtRuntime() {
        Preparation prep = AnnotatedDummy.class.getAnnotation(Preparation.class);
        assertNotNull(prep, "Preparation annotation must be visible at runtime");
        assertEquals("dummy-prep", prep.name());
        assertEquals("1.0.0", prep.version());
        assertEquals("Dummy preparation executor", prep.description());

        Injection inj = AnnotatedDummy.class.getAnnotation(Injection.class);
        assertNotNull(inj, "Injection annotation must be visible at runtime");
        assertEquals("dummy-inj", inj.name());
        assertEquals("2.0.0", inj.version());
        assertEquals("Dummy injection executor", inj.description());

        Assertion asrt = AnnotatedDummy.class.getAnnotation(Assertion.class);
        assertNotNull(asrt, "Assertion annotation must be visible at runtime");
        assertEquals("dummy-asrt", asrt.name());
        assertEquals("1.5.0", asrt.version());
        assertEquals("", asrt.description());
    }

    @Preparation(name = "dummy-prep", description = "Dummy preparation executor")
    @Injection(name = "dummy-inj", version = "2.0.0", description = "Dummy injection executor")
    @Assertion(name = "dummy-asrt", version = "1.5.0")
    private static final class AnnotatedDummy {
    }
}
