package com.performance.platform.scenario.loadmodel;

import com.performance.platform.domain.injection.LoadModel;

import java.util.Map;

/**
 * Registre de load models reutilisables, indexes par nom.
 * Permet a un scenario YAML de referencer un load model par son nom
 * au lieu de dupliquer ses parametres a chaque step.
 */
public interface LoadModelRegistry {

    /**
     * Enregistre un load model sous un nom unique.
     * Si un load model existe deja sous ce nom, il est remplace.
     *
     * @param name  le nom du load model (non-null)
     * @param model le load model a enregistrer (non-null)
     */
    void register(String name, LoadModel model);

    /**
     * Recupere un load model par son nom.
     *
     * @param name le nom du load model
     * @return le LoadModel associe
     * @throws LoadModelNotFoundException si aucun load model n'existe sous ce nom
     */
    LoadModel get(String name) throws LoadModelNotFoundException;

    /**
     * Retourne une copie immuable de tous les load models enregistres.
     *
     * @return une map non modifiable (nom -> LoadModel)
     */
    Map<String, LoadModel> getAll();
}
