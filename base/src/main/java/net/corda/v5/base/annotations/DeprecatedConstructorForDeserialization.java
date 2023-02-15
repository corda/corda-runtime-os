package net.corda.v5.base.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is a marker to indicate which secondary constructors should be considered, and in which
 * order, for evolving objects during their deserialization.
 * <p>
 * Versions will be considered in descending order, currently duplicate versions will result in
 * non-deterministic behaviour when deserializing objects
 */
@Target(CONSTRUCTOR)
@Retention(RUNTIME)
public @interface DeprecatedConstructorForDeserialization {
    /**
     * @return Integer defining order that these annotations will be applied.
     */
    int version();
}

