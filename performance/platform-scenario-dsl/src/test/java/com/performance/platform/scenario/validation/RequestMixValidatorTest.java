package com.performance.platform.scenario.validation;

import com.performance.platform.domain.scenario.Phase;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestMixValidatorTest {

    private final RequestMixValidator validator = new RequestMixValidator();

    private static Map<String, Object> req(String name, Object weight, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (name != null) m.put("name", name);
        if (weight != null) m.put("weight", weight);
        if (path != null) m.put("path", path);
        return m;
    }

    private List<ValidationError> run(Phase phase, Map<String, Object> params) {
        return validator.validate("s1", phase, params);
    }

    private static Map<String, Object> params(Object requests) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("requests", requests);
        return p;
    }

    @Test
    void noBlock_noError() {
        assertTrue(run(Phase.INJECTION, Map.of("path", "x")).isEmpty());
    }

    @Test
    void validMix_noError() {
        var p = params(List.of(req("create", 70, "a"), req("read-1", 0.5, "b")));
        assertTrue(run(Phase.INJECTION, p).isEmpty());
    }

    @Test
    void emptyList_error() {
        assertEquals("step.requests.empty", run(Phase.INJECTION, params(List.of())).get(0).field());
    }

    @Test
    void notAList_error() {
        assertEquals(1, run(Phase.INJECTION, params("x")).size());
    }

    @Test
    void duplicateName_error() {
        var p = params(List.of(req("a", 1, "x"), req("a", 1, "y")));
        assertEquals(1, run(Phase.INJECTION, p).size());
    }

    @Test
    void invalidName_error() {
        assertEquals(1, run(Phase.INJECTION, params(List.of(req("Bad_Name", 1, "x")))).size());
        assertEquals(1, run(Phase.INJECTION, params(List.of(req(null, 1, "x")))).size());
    }

    @Test
    void invalidWeight_error() {
        assertEquals(1, run(Phase.INJECTION, params(List.of(req("a", 0, "x")))).size());
        assertEquals(1, run(Phase.INJECTION, params(List.of(req("a", -3, "x")))).size());
        assertEquals(1, run(Phase.INJECTION, params(List.of(req("a", "abc", "x")))).size());
        assertEquals(1, run(Phase.INJECTION, params(List.of(req("a", null, "x")))).size());
    }

    @Test
    void exclusiveWithSingleForm_error() {
        var p = params(List.of(req("a", 1, "x")));
        p.put("path", "y");
        assertEquals(1, run(Phase.INJECTION, p).size());
        var q = params(List.of(req("a", 1, "x")));
        q.put("method", "GET");
        assertEquals(1, run(Phase.INJECTION, q).size());
    }

    @Test
    void entryRules_likeSingleRequest() {
        assertEquals(1, run(Phase.INJECTION, params(List.of(req("a", 1, null)))).size());
        var badMethod = req("a", 1, "x");
        badMethod.put("method", "FETCH");
        assertEquals(1, run(Phase.INJECTION, params(List.of(badMethod))).size());
        var badHeaders = req("a", 1, "x");
        badHeaders.put("headers", "oops");
        assertEquals(1, run(Phase.INJECTION, params(List.of(badHeaders))).size());
        var badStatus = req("a", 1, "x");
        badStatus.put("expectedStatus", "ok");
        assertEquals(1, run(Phase.INJECTION, params(List.of(badStatus))).size());
        var good = req("a", 1, "x");
        good.put("method", "post");
        good.put("expectedStatus", "201");
        good.put("headers", Map.of("k", "v"));
        assertTrue(run(Phase.INJECTION, params(List.of(good))).isEmpty());
    }

    @Test
    void entryNotMapping_error() {
        assertEquals(1, run(Phase.INJECTION, params(List.of("x"))).size());
    }

    @Test
    void nonInjectionPhase_error() {
        assertEquals(1, run(Phase.PREPARATION, params(List.of(req("a", 1, "x")))).size());
    }
}
