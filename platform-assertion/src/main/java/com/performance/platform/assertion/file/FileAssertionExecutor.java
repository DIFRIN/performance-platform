package com.performance.platform.assertion.file;

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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * CC-02: pipeline cohesif — extraction parametres (path/check/checksum/sizeBytes),
 * dispatch vers 5 checks via switch, operations fichier (exists/size/SHA-256),
 * evaluation predicat, construction resultat. L'extraction en methodes separees
 * fragmenterait des checks deja atomiques (2-3 lignes de logique metier chacun)
 * sans gain de reutilisabilite.
 * <p>
 * Assertion executor pour les assertions sur fichiers.
 * Verifie l'existence, l'absence, la taille ou le checksum SHA-256
 * d'un fichier sur le systeme de fichiers local.
 * <p>
 * Supporte les checks : {@code EXISTS}, {@code NOT_EXISTS},
 * {@code CHECKSUM} (sha256), {@code SIZE_GT}, {@code SIZE_LT}.
 * <p>
 * Annotee {@code @Assertion(name = "file")} pour la decouverte
 * automatique par le {@code DefaultAssertionExecutorRegistry}.
 */
@Component
@Assertion(name = "file",
           description = "File EXISTS/CHECKSUM/SIZE assertions")
public class FileAssertionExecutor implements AssertionExecutor {

    private static final Logger log = LoggerFactory.getLogger(FileAssertionExecutor.class);

    // --- Constantes de parametres ---

    static final String PARAM_PATH = "path";
    static final String PARAM_CHECK = "check";
    static final String PARAM_CHECKSUM = "checksum";
    static final String PARAM_SIZE_BYTES = "sizeBytes";

    // --- Types de checks ---

    static final String CHECK_EXISTS = "EXISTS";
    static final String CHECK_NOT_EXISTS = "NOT_EXISTS";
    static final String CHECK_CHECKSUM = "CHECKSUM";
    static final String CHECK_SIZE_GT = "SIZE_GT";
    static final String CHECK_SIZE_LT = "SIZE_LT";

    // --- Prefixe checksum ---

    static final String CHECKSUM_PREFIX_SHA256 = "sha256:";

    @Override
    public String getSupportedAssertionName() {
        return "file";
    }

    /**
     * CC-02: pipeline cohesif — extraction parametres (path, check,
     * checksum/sizeBytes), operations fichier (exists/size/checksum),
     * evaluation predicat, construction resultat. L'extraction en methodes
     * separees fragmenterait une logique deja simple (chaque check est
     * 2-3 lignes) sans gain de reutilisabilite.
     *
     * @param context le contexte d'execution immuable
     * @param step    la definition de l'etape d'assertion
     * @return le resultat de l'evaluation (PASSED, FAILED, ou ERROR)
     */
    @Override
    public AssertionResult evaluate(ExecutionContext context, StepDefinition step) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(step, "step must not be null");
        Instant start = Instant.now();

        try {
            Map<String, Object> params = step.parameters();

            // 1. Extraire les parametres
            String pathStr = getRequiredStringParam(params, PARAM_PATH);
            String check = getRequiredStringParam(params, PARAM_CHECK).toUpperCase();
            Path filePath = Path.of(pathStr);

            // 2. Executer le check approprie
            return switch (check) {
                case CHECK_EXISTS     -> evaluateExists(step, start, filePath, params);
                case CHECK_NOT_EXISTS -> evaluateNotExists(step, start, filePath, params);
                case CHECK_CHECKSUM   -> evaluateChecksum(context, step, start, filePath, params);
                case CHECK_SIZE_GT    -> evaluateSize(context, step, start, filePath, params,
                        AssertionOperator.GT);
                case CHECK_SIZE_LT    -> evaluateSize(context, step, start, filePath, params,
                        AssertionOperator.LT);
                default -> {
                    log.warn("action=file_assertion_unsupported_check executionId={} "
                             + "assertionId={} check={}",
                             context.executionId().value(), step.id().value(), check);
                    yield buildErrorResult(step, start,
                            "Unsupported check: '" + check
                            + "'. Supported: EXISTS, NOT_EXISTS, CHECKSUM, SIZE_GT, SIZE_LT",
                            params);
                }
            };

        } catch (IllegalArgumentException e) {
            log.warn("action=file_assertion_param_error executionId={} assertionId={} error={}",
                     context.executionId().value(), step.id().value(), e.getMessage());
            return buildErrorResult(step, start, e.getMessage(),
                    step.parameters());
        }
    }

    // --- Evaluations par check ---

    /**
     * Verifie que le fichier existe.
     */
    private AssertionResult evaluateExists(StepDefinition step, Instant start,
                                            Path filePath, Map<String, Object> params) {
        boolean exists = Files.exists(filePath);
        AssertionStatus status = exists ? AssertionStatus.PASSED : AssertionStatus.FAILED;
        String fileState = exists ? "exists" : "does not exist";
        String description = String.format("%s: file %s %s",
                exists ? "PASSED" : "FAILED", filePath, fileState);

        Evidence evidence = new Evidence(
                fileState, "exists", AssertionOperator.EQ,
                null, Map.copyOf(params));

        return buildResult(step, start, status, description, evidence);
    }

    /**
     * Verifie que le fichier n'existe PAS.
     */
    private AssertionResult evaluateNotExists(StepDefinition step, Instant start,
                                               Path filePath, Map<String, Object> params) {
        boolean exists = Files.exists(filePath);
        AssertionStatus status = exists ? AssertionStatus.FAILED : AssertionStatus.PASSED;
        String fileState = exists ? "exists" : "does not exist";
        String description = String.format("%s: file %s %s",
                exists ? "FAILED" : "PASSED", filePath, fileState);

        Evidence evidence = new Evidence(
                fileState, "does not exist", AssertionOperator.EQ,
                null, Map.copyOf(params));

        return buildResult(step, start, status, description, evidence);
    }

    /**
     * CC-02: pipeline cohesif — verification existence fichier,
     * extraction/validation checksum, calcul SHA-256, comparaison,
     * construction resultat.
     * <p>
     * Verifie le checksum SHA-256 du fichier.
     * Format attendu du parametre checksum : {@code "sha256:<hex>"}.
     */
    private AssertionResult evaluateChecksum(ExecutionContext context,
                                              StepDefinition step, Instant start,
                                              Path filePath, Map<String, Object> params) {
        // Verifier que le fichier existe
        if (!Files.exists(filePath)) {
            log.warn("action=file_assertion_checksum_file_missing executionId={} path={}",
                     context.executionId().value(), filePath);
            return buildErrorResult(step, start,
                    "File not found for CHECKSUM: " + filePath,
                    params);
        }

        // Extraire le checksum attendu
        String checksumParam = getRequiredStringParam(params, PARAM_CHECKSUM);
        if (!checksumParam.startsWith(CHECKSUM_PREFIX_SHA256)) {
            return buildErrorResult(step, start,
                    "Checksum must start with 'sha256:', got: " + checksumParam,
                    params);
        }
        String expectedHex = checksumParam.substring(CHECKSUM_PREFIX_SHA256.length());
        if (expectedHex.isBlank()) {
            return buildErrorResult(step, start,
                    "Checksum hex value is empty after 'sha256:' prefix",
                    params);
        }

        // Calculer le checksum reel
        String actualHex;
        try {
            actualHex = sha256Hex(filePath);
        } catch (IOException e) {
            log.warn("action=file_assertion_checksum_read_error executionId={} path={} error={}",
                     context.executionId().value(), filePath, e.getMessage());
            return buildErrorResult(step, start,
                    "Failed to read file for checksum: " + e.getMessage(),
                    params);
        }

        boolean match = actualHex.equalsIgnoreCase(expectedHex);
        AssertionStatus status = match ? AssertionStatus.PASSED : AssertionStatus.FAILED;
        String description = String.format("%s: checksum %s (expected %s) %s",
                match ? "PASSED" : "FAILED", actualHex.substring(0, 8) + "...",
                expectedHex.substring(0, 8) + "...",
                match ? "matches" : "mismatch");

        Evidence evidence = new Evidence(
                "sha256:" + actualHex,
                checksumParam,
                AssertionOperator.EQ,
                null,
                Map.copyOf(params));

        return buildResult(step, start, status, description, evidence);
    }

    /**
     * CC-02: pipeline cohesif — verification existence fichier,
     * extraction sizeBytes, lecture taille fichier, evaluation
     * operateur (GT/LT), construction resultat.
     * <p>
     * Verifie la taille du fichier (GT ou LT).
     */
    private AssertionResult evaluateSize(ExecutionContext context,
                                          StepDefinition step, Instant start,
                                          Path filePath, Map<String, Object> params,
                                          AssertionOperator operator) {
        // Verifier que le fichier existe
        if (!Files.exists(filePath)) {
            log.warn("action=file_assertion_size_file_missing executionId={} path={}",
                     context.executionId().value(), filePath);
            return buildErrorResult(step, start,
                    "File not found for size check: " + filePath,
                    params);
        }

        double sizeBytes = getRequiredDoubleParam(params, PARAM_SIZE_BYTES);

        long actualSize;
        try {
            actualSize = Files.size(filePath);
        } catch (IOException e) {
            log.warn("action=file_assertion_size_read_error executionId={} path={} error={}",
                     context.executionId().value(), filePath, e.getMessage());
            return buildErrorResult(step, start,
                    "Failed to read file size: " + e.getMessage(),
                    params);
        }

        boolean passed = operator.evaluate((double) actualSize, sizeBytes);
        AssertionStatus status = passed ? AssertionStatus.PASSED : AssertionStatus.FAILED;
        String operatorStr = operator == AssertionOperator.GT ? ">" : "<";
        String description = String.format("%s: size %,d bytes %s %,d bytes",
                passed ? "PASSED" : "FAILED", actualSize,
                operatorStr, (long) sizeBytes);

        Evidence evidence = new Evidence(
                (double) actualSize,
                sizeBytes,
                operator,
                "bytes",
                Map.copyOf(params));

        return buildResult(step, start, status, description, evidence);
    }

    // --- Checksum SHA-256 ---

    /**
     * Calcule le hash SHA-256 d'un fichier.
     *
     * @param filePath le chemin du fichier
     * @return la representation hexadecimale du hash
     * @throws IOException en cas d'erreur de lecture
     */
    private String sha256Hex(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream in = Files.newInputStream(filePath)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
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

    // --- Helpers de construction ---

    private AssertionResult buildResult(StepDefinition step, Instant start,
                                         AssertionStatus status, String description,
                                         Evidence evidence) {
        Duration duration = Duration.between(start, Instant.now());
        return new AssertionResult(
                step.id(),
                status,
                description,
                evidence,
                duration,
                Instant.now());
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
}
