package net.corda.v5.application.marshalling.json;

import org.jetbrains.annotations.NotNull;

/**
 * An interface to a custom serializer of objects of the specified type T into Json.
 */
public interface JsonSerializer<T> {
    /**
     * Method called when an object of type T should be serialized.
     *
     * @param item The object to serialize.
     * @param jsonWriter An interface to a writer of Json which should be used to translate fields in item of type T
     * to Json.
     */
    void serialize(T item, @NotNull JsonWriter jsonWriter);
}
