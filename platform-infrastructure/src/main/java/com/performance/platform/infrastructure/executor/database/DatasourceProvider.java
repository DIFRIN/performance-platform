package com.performance.platform.infrastructure.executor.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre de datasources nommées, résolues par le {@link DatabaseTaskExecutor}.
 * <p>
 * Chaque datasource est enregistrée avec un nom logique (ex: "default", "warehouse")
 * correspondant à la clé {@code datasource} dans les paramètres de l'étape.
 * <p>
 * Thread-safe : utilise {@link ConcurrentHashMap} pour l'enregistrement concurrent.
 * <p>
 * Ce n'est <strong>plus</strong> un {@code @Component} — il est instancié par
 * {@link DatasourceConfiguration} (ADR-014).
 */
public class DatasourceProvider {

    private static final Logger log = LoggerFactory.getLogger(DatasourceProvider.class);

    private final Map<String, DataSource> datasources = new ConcurrentHashMap<>();

    /**
     * Enregistre une datasource nommée. Si une datasource portant le même nom existe
     * déjà, elle est remplacée et un avertissement est loggé.
     *
     * @param name       nom logique de la datasource (jamais null)
     * @param dataSource la datasource à enregistrer (jamais null)
     * @throws NullPointerException si name ou dataSource est null
     */
    public void register(String name, DataSource dataSource) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(dataSource, "dataSource must not be null");

        DataSource previous = datasources.put(name, dataSource);
        if (previous != null) {
            log.warn("action=datasource_replaced name={} previousClass={} newClass={}",
                    name, previous.getClass().getSimpleName(), dataSource.getClass().getSimpleName());
        } else {
            log.info("action=datasource_registered name={} class={}",
                    name, dataSource.getClass().getSimpleName());
        }
    }

    /**
     * Résout une datasource par son nom logique.
     *
     * @param name le nom logique de la datasource
     * @return la datasource, ou {@code null} si aucune datasource n'est enregistrée sous ce nom
     */
    public DataSource get(String name) {
        return datasources.get(name);
    }

    /**
     * Indique si une datasource est enregistrée sous le nom donné.
     *
     * @param name le nom logique
     * @return {@code true} si une datasource correspondante existe
     */
    public boolean contains(String name) {
        return datasources.containsKey(name);
    }
}
