/**
 * TaskExecutor implementations — adapters between scenario steps and external systems.
 *
 * <h2>Executors disponibles</h2>
 * <table>
 *   <tr><th>Classe</th><th>taskName</th><th>Phase</th><th>Quand l'utiliser</th></tr>
 *   <tr><td>{@link database.DatabaseTaskExecutor}</td><td>database</td><td>PREPARATION</td>
 *       <td>Purge/populate DB avec validation SQL anti-injection et timeout</td></tr>
 *   <tr><td>{@link docker.DockerTaskExecutor}</td><td>docker</td><td>PREPARATION</td>
 *       <td>Conteneurs avec healthcheck, safe-stop et tracking par execution</td></tr>
 *   <tr><td>{@link fs.FilesystemTaskExecutor}</td><td>filesystem</td><td>PREPARATION</td>
 *       <td>Operations fichier avec sandboxing (tracking des chemins crees)</td></tr>
 *   <tr><td>{@link http.HttpClientTaskExecutor}</td><td>http-client</td><td>PREPARATION</td>
 *       <td>Requetes HTTP avec timeout, retry et gestion des headers</td></tr>
 *   <tr><td>{@link kafka.KafkaConsumerTaskExecutor}</td><td>kafka-consumer</td><td>PREPARATION</td>
 *       <td>Consommation Kafka avec consumer groups et registry de clusters</td></tr>
 *   <tr><td>{@link kafka.KafkaProducerTaskExecutor}</td><td>kafka-producer</td><td>PREPARATION</td>
 *       <td>Production Kafka avec cluster registry et validation de schema</td></tr>
 *   <tr><td>{@link mock.MockServerTaskExecutor}</td><td>mock-server</td><td>PREPARATION</td>
 *       <td>Mock HTTP avec WireMock lifecycle (start/stop/reset)</td></tr>
 *   <tr><td>{@link shell.ShellTaskExecutor}</td><td>shell</td><td>PREPARATION</td>
 *       <td>Fallback universel : commandes shell generiques avec timeout et capture stdout/stderr</td></tr>
 * </table>
 *
 * <h2>Doctrine</h2>
 * Voir ADR-022 pour la doctrine dedie vs Shell.
 *
 * <p>Tous les executors implementent {@link com.performance.platform.plugin.TaskExecutor}
 * et sont decouverts automatiquement par Spring via {@code @Component} et
 * l'annotation {@code @Preparation}.
 *
 * @see com.performance.platform.plugin.TaskExecutor
 * @see com.performance.platform.plugin.Preparation
 */
package com.performance.platform.infrastructure.executor;
