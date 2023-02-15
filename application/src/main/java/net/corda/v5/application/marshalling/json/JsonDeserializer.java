package net.corda.v5.application.marshalling.json;

import org.jetbrains.annotations.NotNull;

/**
 * An interface to a custom deserializer of objects of the specified type T from Json.
 */
public interface JsonDeserializer<T> {
    /**
     * Method called when an object of type T should be deserialized.
     *
     * @param jsonRoot The Json object to deserialize. The methods of {@link JsonNodeReader} should be used to traverse the
     * Json and extract fields to create an instance of a T to return.
     *
     * @return an object of type T which has been deserialized from Json
     */
    T deserialize(@NotNull JsonNodeReader jsonRoot);
}
