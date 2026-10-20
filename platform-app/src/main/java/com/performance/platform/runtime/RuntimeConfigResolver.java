package com.performance.platform.runtime;

import org.springframework.core.env.Environment;

import java.util.Objects;

/**
 * Resolution unique du mode, du role et du transport (ADR-032), avec la
 * priorite {@code env var > argument CLI > property > defaut} (ADR-006).
 * <p>
 * Deux points d'entree :
 * <ul>
 *   <li>{@link #resolve(String[])} pour les beans, adosse a l'{@link Environment}
 *       Spring (qui contient deja les arguments CLI) ;</li>
 *   <li>{@link #resolveBootstrapRole(String[], String, String)} pour
 *       {@code main()}, avant la construction de l'{@code Environment} —
 *       indispensable pour choisir le {@code WebApplicationType} (ADR-019).</li>
 * </ul>
 * Les noms d'environnement et de proprietes sont centralises ici : le
 * post-processeur d'environnement et {@link RuntimeModeResolver} les reutilisent.
 */
public final class RuntimeConfigResolver {

    public static final String ENV_RUNTIME_MODE = "RUNTIME_MODE";
    public static final String ENV_MODE = "MODE";
    public static final String ENV_TRANSPORT_TYPE = "TRANSPORT_TYPE";

    public static final String PROP_RUNTIME_MODE = "runtime.mode";
    public static final String PROP_RUNTIME_ROLE = "runtime.role";
    public static final String PROP_TRANSPORT_TYPE = "transport.type";

    public static final String CLI_RUNTIME_MODE = "--runtime.mode=";
    public static final String CLI_RUNTIME_ROLE = "--runtime.role=";
    public static final String CLI_TRANSPORT_TYPE = "--transport.type=";
    public static final String CLI_PROFILES = "--spring.profiles.active=";

    private final Environment environment;

    public RuntimeConfigResolver(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    /**
     * Resout la configuration complete.
     *
     * @param cliArgs arguments CLI (peut etre null)
     * @return la configuration resolue
     */
    public RuntimeConfig resolve(String[] cliArgs) {
        return new RuntimeConfig(
                resolveMode(cliArgs),
                resolveRole(cliArgs),
                resolveTransportType(cliArgs));
    }

    public RuntimeMode resolveMode(String[] cliArgs) {
        String env = environment.getProperty(ENV_RUNTIME_MODE);
        if (hasText(env)) {
            return parseMode(env);
        }
        String cli = cliValue(cliArgs, CLI_RUNTIME_MODE);
        if (hasText(cli)) {
            return parseMode(cli);
        }
        String property = environment.getProperty(PROP_RUNTIME_MODE);
        if (hasText(property)) {
            return parseMode(property);
        }
        return RuntimeMode.LOCAL;
    }

    public RuntimeRole resolveRole(String[] cliArgs) {
        String env = environment.getProperty(ENV_MODE);
        if (hasText(env)) {
            return parseRole(env);
        }
        String cli = cliValue(cliArgs, CLI_RUNTIME_ROLE);
        if (hasText(cli)) {
            return parseRole(cli);
        }
        String property = environment.getProperty(PROP_RUNTIME_ROLE);
        if (hasText(property)) {
            return parseRole(property);
        }
        if (cliProfileHasAgent(cliArgs)) {
            return RuntimeRole.AGENT;
        }
        return RuntimeRole.NONE;
    }

    public String resolveTransportType(String[] cliArgs) {
        String env = environment.getProperty(ENV_TRANSPORT_TYPE);
        if (hasText(env)) {
            return env.strip();
        }
        String cli = cliValue(cliArgs, CLI_TRANSPORT_TYPE);
        if (hasText(cli)) {
            return cli.strip();
        }
        String property = environment.getProperty(PROP_TRANSPORT_TYPE);
        return hasText(property) ? property.strip() : null;
    }

    // ------------------------------------------------------------------
    // Bootstrap (avant Spring)
    // ------------------------------------------------------------------

    /**
     * Resout le role sans {@link Environment}, a partir des valeurs brutes.
     * Priorite : {@code MODE} env > propriete systeme {@code runtime.role}
     * > argument CLI > profil {@code agent} > {@code NONE}.
     */
    public static RuntimeRole resolveBootstrapRole(String[] cliArgs,
                                                   String envMode,
                                                   String roleProperty) {
        if (hasText(envMode)) {
            return parseRole(envMode);
        }
        if (hasText(roleProperty)) {
            return parseRole(roleProperty);
        }
        String cli = cliValue(cliArgs, CLI_RUNTIME_ROLE);
        if (hasText(cli)) {
            return parseRole(cli);
        }
        return cliProfileHasAgent(cliArgs) ? RuntimeRole.AGENT : RuntimeRole.NONE;
    }

    public static boolean isAgent(String[] cliArgs, String envMode, String roleProperty) {
        return resolveBootstrapRole(cliArgs, envMode, roleProperty) == RuntimeRole.AGENT;
    }

    // ------------------------------------------------------------------
    // Parsing partage
    // ------------------------------------------------------------------

    public static RuntimeMode parseMode(String value) {
        try {
            return RuntimeMode.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid runtime mode: '" + value + "'. Expected LOCAL or DISTRIBUTED.");
        }
    }

    public static RuntimeRole parseRole(String value) {
        try {
            return RuntimeRole.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid runtime role: '" + value + "'. Expected ORCHESTRATOR, AGENT, or NONE.");
        }
    }

    static String cliValue(String[] args, String prefix) {
        if (args == null) {
            return null;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return null;
    }

    static boolean cliProfileHasAgent(String[] args) {
        return isAgentProfile(cliValue(args, CLI_PROFILES));
    }

    /**
     * Indique si une liste de profils Spring designe un agent.
     * <p>
     * Reconnait {@code agent} ainsi que les profils specialises
     * {@code agent-injector}, {@code agent-asserter}, etc. Cette detection
     * est utilisee avant le demarrage de Spring pour choisir
     * {@code WebApplicationType.NONE} (ADR-019).
     *
     * @param profiles valeur de {@code spring.profiles.active} (peut etre null)
     * @return true si au moins un profil est un profil agent
     */
    public static boolean isAgentProfile(String profiles) {
        if (profiles == null) {
            return false;
        }
        for (String profile : profiles.split(",")) {
            String value = profile.strip().toLowerCase();
            if (value.equals("agent")
                    || value.startsWith("agent-")
                    || value.startsWith("agent_")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
