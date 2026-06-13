package com.performance.platform.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an injection (load generation) task executor.
 * The {@link #name()} value is used as the key to resolve
 * injection steps in scenario YAML files.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Injection {

    /** Clé de résolution dans le registre et dans le YAML (obligatoire). */
    String name();

    /** Version du plugin. */
    String version() default "1.0.0";

    /** Description lisible du plugin. */
    String description() default "";
}
