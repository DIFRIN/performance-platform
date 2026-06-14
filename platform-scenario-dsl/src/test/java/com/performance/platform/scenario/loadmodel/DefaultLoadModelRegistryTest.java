package com.performance.platform.scenario.loadmodel;

import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultLoadModelRegistry")
class DefaultLoadModelRegistryTest {

    private LoadModelRegistry registry;

    private static LoadModel createModel(String key, String value) {
        return new LoadModel(LoadModelType.CONSTANT, Map.of(key, value));
    }

    @BeforeEach
    void setUp() {
        registry = new DefaultLoadModelRegistry();
    }

    @Nested
    @DisplayName("register + get")
    class RegisterAndGet {

        @Test
        @DisplayName("should return registered model by name")
        void shouldReturnRegisteredModel() {
            LoadModel model = createModel("users", "10");
            registry.register("ramp-up", model);

            LoadModel result = registry.get("ramp-up");

            assertSame(model, result);
        }

        @Test
        @DisplayName("should replace model when registering with same name")
        void shouldReplaceModelOnDuplicateName() {
            LoadModel first = createModel("users", "10");
            LoadModel second = createModel("users", "20");

            registry.register("ramp-up", first);
            registry.register("ramp-up", second);

            assertSame(second, registry.get("ramp-up"));
        }

        @Test
        @DisplayName("should distinguish models by name")
        void shouldDistinguishByName() {
            LoadModel a = createModel("rate", "5");
            LoadModel b = createModel("rate", "50");

            registry.register("low", a);
            registry.register("high", b);

            assertSame(a, registry.get("low"));
            assertSame(b, registry.get("high"));
        }
    }

    @Nested
    @DisplayName("get absent")
    class GetAbsent {

        @Test
        @DisplayName("should throw LoadModelNotFoundException when name absent")
        void shouldThrowWhenNameAbsent() {
            LoadModelNotFoundException ex = assertThrows(
                    LoadModelNotFoundException.class,
                    () -> registry.get("absent")
            );

            assertEquals("LoadModel not found: absent", ex.getMessage());
            assertEquals("absent", ex.getName());
        }

        @Test
        @DisplayName("should throw NullPointerException when name is null")
        void shouldThrowNPEWhenNameNull() {
            assertThrows(NullPointerException.class, () -> registry.get(null));
        }

        @Test
        @DisplayName("should throw when registry is empty")
        void shouldThrowWhenRegistryEmpty() {
            LoadModelNotFoundException ex = assertThrows(
                    LoadModelNotFoundException.class,
                    () -> registry.get("anything")
            );
            assertEquals("anything", ex.getName());
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("should return empty map when nothing registered")
        void shouldReturnEmptyMap() {
            Map<String, LoadModel> all = registry.getAll();
            assertTrue(all.isEmpty());
        }

        @Test
        @DisplayName("should return all registered models")
        void shouldReturnAllModels() {
            LoadModel a = createModel("users", "10");
            LoadModel b = createModel("users", "100");

            registry.register("warm", a);
            registry.register("peak", b);

            Map<String, LoadModel> all = registry.getAll();
            assertEquals(2, all.size());
            assertSame(a, all.get("warm"));
            assertSame(b, all.get("peak"));
        }

        @Test
        @DisplayName("should return unmodifiable copy")
        void shouldReturnUnmodifiableCopy() {
            registry.register("test", createModel("rate", "5"));

            Map<String, LoadModel> all = registry.getAll();

            assertThrows(UnsupportedOperationException.class, () -> all.put("extra", createModel("x", "y")));
        }

        @Test
        @DisplayName("should not reflect later registrations")
        void shouldBeSnapshot() {
            registry.register("first", createModel("users", "5"));

            Map<String, LoadModel> snapshot = registry.getAll();

            registry.register("second", createModel("users", "10"));

            assertEquals(1, snapshot.size());
            assertTrue(snapshot.containsKey("first"));
            assertFalse(snapshot.containsKey("second"));
        }
    }

    @Nested
    @DisplayName("register null checks")
    class RegisterNullChecks {

        @Test
        @DisplayName("should throw NPE when name is null")
        void shouldThrowNPEWhenNameNull() {
            assertThrows(NullPointerException.class,
                    () -> registry.register(null, createModel("x", "y")));
        }

        @Test
        @DisplayName("should throw NPE when model is null")
        void shouldThrowNPEWhenModelNull() {
            assertThrows(NullPointerException.class,
                    () -> registry.register("test", null));
        }
    }
}
