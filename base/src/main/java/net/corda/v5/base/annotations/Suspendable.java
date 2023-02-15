package net.corda.v5.base.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as suspendable.
 * <p>
 * This annotation is required to allow a fiber (a special lightweight thread used by flows in Corda) to suspend and
 * release the underlying thread to another fiber.
 */
@Target({ TYPE, METHOD })
@Retention(RUNTIME)
@Documented
public @interface Suspendable {
}
