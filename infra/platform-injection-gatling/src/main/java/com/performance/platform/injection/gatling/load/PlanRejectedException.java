package com.performance.platform.injection.gatling.load;

/** Plan de charge non executable par l'adaptateur. Le message nomme la fonctionnalite refusee. */
public class PlanRejectedException extends RuntimeException {

    private final String feature;

    public PlanRejectedException(String feature, String message) {
        super(message);
        this.feature = feature;
    }

    /** Nom de la fonctionnalite non supportee. */
    public String feature() {
        return feature;
    }
}
