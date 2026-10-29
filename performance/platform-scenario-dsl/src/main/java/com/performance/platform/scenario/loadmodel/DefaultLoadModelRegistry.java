package com.performance.platform.scenario.loadmodel;

import com.performance.platform.domain.injection.LoadModel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation en memoire de LoadModelRegistry utilisant une ConcurrentHashMap.
 * Thread-safe : les appels concurrents a register/get/getAll sont sans risque.
 */
public class DefaultLoadModelRegistry implements LoadModelRegistry {

    private final Map<String, LoadModel> storage = new ConcurrentHashMap<>();

    @Override
    public void register(String name, LoadModel model) {
        Objects.requireNonNull(name, "name required");
        Objects.requireNonNull(model, "model required");
        storage.put(name, model);
    }

    @Override
    public LoadModel get(String name) throws LoadModelNotFoundException {
        Objects.requireNonNull(name, "name required");
        LoadModel model = storage.get(name);
        if (model == null) {
            throw new LoadModelNotFoundException(name);
        }
        return model;
    }

    @Override
    public Map<String, LoadModel> getAll() {
        return Map.copyOf(storage);
    }
}
