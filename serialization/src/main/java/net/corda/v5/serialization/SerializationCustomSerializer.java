package net.corda.v5.serialization;

import org.jetbrains.annotations.NotNull;

/**
 * Allows CorDapps to provide custom serializers for third party libraries where those libraries cannot
 * be recompiled with the -parameters flag rendering their classes natively serializable by Corda. In this case
 * a proxy serializer can be written that extends this type whose purpose is to move between those an
 * unserializable types and an intermediate representation.
 *
 * NOTE: The proxy object should be specified as a separate class. However, this can be defined within the
 * scope of the custom serializer.
 */
public interface SerializationCustomSerializer<OBJ, PROXY> {
    /**
     * Should facilitate the conversion of the third party object into the serializable
     * local class specified by {@code PROXY}
     */
    @NotNull
    PROXY toProxy(@NotNull OBJ obj);

    /**
     * Should facilitate the conversion of the proxy object into a new instance of the
     * unserializable type
     */
    @NotNull
    OBJ fromProxy(@NotNull PROXY proxy);
}
