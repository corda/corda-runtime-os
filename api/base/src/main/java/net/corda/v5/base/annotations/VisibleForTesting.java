package net.corda.v5.base.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * The annotated entity should have a more restricted visibility but needs to be visible for tests.
 * <p>
 * This annotation is to be used on any class, method or property that needs package or public visibilty
 * purely for testing purpose, and should only be used internally in production code.
 * Technically, it has no effect on the code - there is no mechanism to enforce that the annotated entity is only used
 * where it is intended to be used. It is purely a marker that the visibility of the annotated entity is chosen for
 * testing purposes, and it should not be relied upon for API purposes.
 */
@Target({ TYPE, CONSTRUCTOR, METHOD })
@Retention(SOURCE)
@Documented
public @interface VisibleForTesting {
}
