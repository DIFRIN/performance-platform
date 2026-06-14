package com.performance.platform.scenario.loadmodel;

/**
 * Levee quand un load model est demande par un nom absent du registre.
 */
public class LoadModelNotFoundException extends RuntimeException {

    private final String name;

    public LoadModelNotFoundException(String name) {
        super("LoadModel not found: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
