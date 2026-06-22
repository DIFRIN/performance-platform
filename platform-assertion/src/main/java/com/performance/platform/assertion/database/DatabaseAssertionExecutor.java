package com.performance.platform.assertion.database;

import com.performance.platform.domain.assertion.AssertionOperator;
import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.assertion.AssertionStatus;
import com.performance.platform.domain.assertion.Evidence;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.plugin.Assertion;
import com.performance.platform.plugin.AssertionExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * CC-02: pipeline cohesif — resolution datasource, execution requete SQL,
 * extraction valeur, evaluation operateur, construction resultat.
 * L'extraction en methodes separees complexifierait la gestion des
 * ressources JDBC (try-with-resources) sans gain de reutilisabilite.
 * <p>
 * Assertion executor pour les assertions base de donnees.
 * Execute une requete SQL (typiquement un COUNT), extrait une valeur
 * numerique, et la compare a un seuil via un {@link AssertionOperator}.
 * <p>
 * Toute operation I/O bloquante (connexion DB, execution SQL) est
 * executee sous Virtual Threads.
 * <p>
 * Annotee {@code @Assertion(name = "database")} pour la
 * decouverte automatique par le {@code DefaultAssertionExecutorRegistry}.
 */
@Component
@Assertion(name = "database",
           description = "SQL count/exists assertions")
public class DatabaseAssertionExecutor implements AssertionExecutor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAssertionExecutor.class);

    // --- Constantes de parametres ---

    static final String PARAM_DATASOURCE = "datasource";
    static final String PARAM_QUERY = "query";
    static final String PARAM_OPERATOR = "operator";
    static final String PARAM_VALUE = "value";
    static final String PARAM_UNIT = "unit";

    private final ApplicationContext applicationContext;

    public DatabaseAssertionExecutor(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext,
                "applicationContext must not be null");
    }

    @Override
    public String getSupportedAssertionName() {
        return "database";
    }

    @Override
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");
        Instant start = Instant.now();

        try {
            Map<String, Object> params = step.parameters();

            // 1. Extraire et valider les parametres
            String datasourceName = getRequiredStringParam(params, PARAM_DATASOURCE);
            String query = getRequiredStringParam(params, PARAM_QUERY);
            String operatorStr = getRequiredStringParam(params, PARAM_OPERATOR);
            double expectedValue = getRequiredDoubleParam(params, PARAM_VALUE);
            String unit = getStringParam(params, PARAM_UNIT, "rows");

            // 2. Resoudre l'operateur
            AssertionOperator operator = resolveOperator(operatorStr);

            // 3. Resoudre la DataSource
            DataSource dataSource = resolveDataSource(datasourceName);
            if (dataSource == null) {
                return buildErrorResult(step, start,
                        "DataSource not found: '" + datasourceName + "'",
                        params);
            }

            // 4. Executer la requete SQL sous Virtual Thread
            double actualValue = executeQuery(dataSource, query);

            // 5. Evaluer l'operateur
            boolean passed = operator.evaluate(actualValue, expectedValue);

            // 6. Construire l'evidence
            var details = new HashMap<String, Object>();
            details.put("datasource", datasourceName);
            details.put("query", query);
            details.put("unit", unit);

            var evidence = new Evidence(
                    actualValue,
                    expectedValue,
                    operator,
                    unit,
                    Map.copyOf(details));

            // 7. Construire la description et le resultat
            AssertionStatus status = passed ? AssertionStatus.PASSED : AssertionStatus.FAILED;
            String description = buildDescription(query, actualValue,
                    expectedValue, operator, passed);

            Duration evaluationDuration = Duration.between(start, Instant.now());

            log.info("action=db_assertion_evaluated executionId={} assertionId={} "
                     + "datasource={} actual={} expected={} operator={} status={}",
                     context.executionId().value(), step.id().value(),
                     datasourceName, actualValue, expectedValue, operatorStr, status);

            return new AssertionResult(
                    step.id(),
                    status,
                    description,
                    evidence,
                    evaluationDuration,
                    Instant.now());

        } catch (IllegalArgumentException e) {
            log.warn("action=db_assertion_param_error executionId={} assertionId={} error={}",
                     context.executionId().value(), step.id().value(), e.getMessage());
            return buildErrorResult(step, start, e.getMessage(),
                    step.parameters());
        } catch (SQLException e) {
            log.error("action=db_assertion_sql_error executionId={} assertionId={} error={}",
                      context.executionId().value(), step.id().value(), e.getMessage());
            return buildErrorResult(step, start,
                    "SQL error: " + e.getMessage(),
                    step.parameters());
        }
    }

    // --- Resolution DataSource ---

    /**
     * Resout une {@link DataSource} par son nom de bean Spring.
     *
     * @param datasourceName le nom du bean DataSource
     * @return la DataSource, ou null si non trouvee
     */
    DataSource resolveDataSource(String datasourceName) {
        try {
            return applicationContext.getBean(datasourceName, DataSource.class);
        } catch (Exception e) {
            log.warn("action=db_datasource_not_found datasource={} error={}",
                     datasourceName, e.getMessage());
            return null;
        }
    }

    // --- Execution SQL ---

    /**
     * Execute la requete SQL et retourne la premiere valeur numerique
     * de la premiere ligne du {@link ResultSet}.
     * <p>
     * L'execution est faite sous Virtual Thread pour ne pas bloquer
     * la plateforme.
     *
     * @param dataSource la datasource
     * @param query      la requete SQL
     * @return la valeur numerique
     * @throws SQLException en cas d'erreur SQL
     */
    double executeQuery(DataSource dataSource, String query) throws SQLException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Double> future = executor.submit(() -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {

                    if (!rs.next()) {
                        throw new SQLException(
                                "Query returned no rows: " + query);
                    }
                    double value = rs.getDouble(1);
                    if (rs.wasNull()) {
                        throw new SQLException(
                                "Query returned NULL for first column: " + query);
                    }
                    log.debug("action=db_query_executed query={} result={}", query, value);
                    return value;
                }
            });
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Query execution interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof SQLException sqle) {
                throw sqle;
            }
            throw new SQLException("Query execution failed: " + e.getMessage(), e.getCause());
        }
    }

    // --- Helpers de parametres ---

    private String getRequiredStringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter: '" + key + "'");
        }
        if (!(value instanceof String str) || str.isEmpty()) {
            throw new IllegalArgumentException(
                    "Parameter '" + key + "' must be a non-empty string, got: " + value);
        }
        return str;
    }

    private double getRequiredDoubleParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter: '" + key + "'");
        }
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        throw new IllegalArgumentException(
                "Parameter '" + key + "' must be a number, got: "
                + value.getClass().getSimpleName());
    }

    private String getStringParam(Map<String, Object> params,
                                   String key, String defaultValue) {
        Object value = params.get(key);
        if (value instanceof String str && !str.isEmpty()) {
            return str;
        }
        return defaultValue;
    }

    private AssertionOperator resolveOperator(String operatorStr) {
        try {
            return AssertionOperator.valueOf(operatorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported operator: '" + operatorStr
                    + "'. Supported: LT, LTE, GT, GTE, EQ, NEQ");
        }
    }

    private AssertionResult buildErrorResult(StepDefinition step,
                                              Instant start,
                                              String errorMessage,
                                              Map<String, Object> params) {
        Duration duration = Duration.between(start, Instant.now());
        return new AssertionResult(
                step.id(),
                AssertionStatus.ERROR,
                errorMessage,
                new Evidence(null, null, AssertionOperator.EQ, null,
                        Map.copyOf(params)),
                duration,
                Instant.now());
    }

    private String buildDescription(String query, double actual,
                                     double expected, AssertionOperator operator,
                                     boolean passed) {
        String verdict = passed ? "PASSED" : "FAILED";
        String shortQuery = query.length() > 60 ? query.substring(0, 57) + "..." : query;
        return String.format("%s: SQL result %.2f %s %.2f [%s]",
                verdict, actual, operatorSymbol(operator), expected, shortQuery);
    }

    private String operatorSymbol(AssertionOperator operator) {
        return switch (operator) {
            case LT  -> "<";
            case LTE -> "<=";
            case GT  -> ">";
            case GTE -> ">=";
            case EQ  -> "==";
            case NEQ -> "!=";
        };
    }
}
