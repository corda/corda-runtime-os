package net.corda.v5.serialization;

import org.jetbrains.annotations.NotNull;

/**
 * Allows CorDapps to provide custom serializers for third-party libraries that cannot
 * be recompiled with the -parameters flag rendering their classes natively serializable by Corda. In this case,
 * a proxy serializer can be written that extends this type whose purpose is to move between those
 * unserializable types and an intermediate representation.
 *
 * NOTE: The proxy object should be specified as a separate class. However, this can be defined within the
 * scope of the custom serializer.
 */
public interface SerializationCustomSerializer<OBJ, PROXY> {
    /**
     * Facilitates the conversion of the third-party object into the serializable
     * local class specified by {@code PROXY}.
     * @param obj Original object for serialization.
     * @return proxy object to be written to AMQP.
     */
    @NotNull
    PROXY toProxy(@NotNull OBJ obj);

    /**
     * Facilitates the conversion of the proxy object into a new instance of the
     * unserializable type.
     * @param proxy Object from AMQP.
     * @return original object recreated from {@code proxy}.
     */
    @NotNull
    OBJ fromProxy(@NotNull PROXY proxy);
}
