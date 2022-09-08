package net.corda.v5.serialization.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is used to mark a class as having had multiple elements renamed as a container annotation for
 * instances of {@link CordaSerializationTransformRename}, each of which details an individual rename.
 * <p/>
 * NOTE: Order is important, new values should always be added before existing
 * <p/>
 * IMPORTANT - Once added (and in production) do NOT remove old annotations. See documentation for
 * more discussion on this point!.
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface CordaSerializationTransformRenames {
    /**
     * @return value an array of {@link CordaSerializationTransformRename}
     */
    CordaSerializationTransformRename[] value();
}
