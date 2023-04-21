package net.corda.v5.base.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * This annotation is for interfaces and abstract classes that provide Corda functionality
 * to user applications.
 * <p>
 * Future versions of Corda may add new methods to such interfaces and
 * classes, but will not remove or modify existing methods.
 * Adding new methods does not break Corda's API compatibility guarantee because applications
 * should not implement or extend anything annotated with {@link DoNotImplement}. These classes are
 * only meant to be implemented by Corda itself.
 */
@Retention(CLASS)
@Target(TYPE)
@Documented
@Inherited
public @interface DoNotImplement {
}
