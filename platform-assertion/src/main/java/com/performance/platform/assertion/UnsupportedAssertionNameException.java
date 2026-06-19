package com.performance.platform.assertion;

/**
 * Levee quand aucun {@link AssertionExecutor} n'est enregistre pour un
 * {@code assertionName} donne.
 * RuntimeException — cette situation est une erreur de configuration,
 * pas un cas metier.
 */
public class UnsupportedAssertionNameException extends RuntimeException {

    private final String assertionName;

    public UnsupportedAssertionNameException(String assertionName) {
        super("No AssertionExecutor registered for assertionName: " + assertionName);
        this.assertionName = assertionName;
    }

    /**
     * @return l'assertionName pour lequel aucun executor n'a ete trouve
     */
    public String getAssertionName() {
        return assertionName;
    }
}
