package net.corda.v5.serialization.annotations;

// TODO When we have class renaming update the docs

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is used to mark a class has having had a property element. It is used by the
 * AMQP deserializer to allow instances with different versions of the class on their Class Path
 * to successfully deserialize the object.
 *
 * NOTE: Renaming of the class itself isn't done with this annotation or, at present, supported
 * by Corda
 */
@Target(TYPE)
@Retention(RUNTIME)
@Repeatable(CordaSerializationTransformRenames.class)
public @interface CordaSerializationTransformRename {
    /**
     * to
     * @return {@link String} representation of the properties new name
     */
    String to();

    /**
     * from
     * @return {@link String} representation of the properties old new
     */
    String from();
}
