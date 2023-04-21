package net.corda.v5.application.flows;

import net.corda.v5.application.marshalling.MarshallingService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * {@link ClientRequestBody} wraps the `requestData` parameter of the HTTP call that triggered a {@link ClientStartableFlow}.
 * <p>
 * A {@link ClientStartableFlow} receives an instance of this interface, which can be used to retrieve the request body.
 *
 * @see ClientStartableFlow
 */
public interface ClientRequestBody {

    /**
     * Gets the request body for the {@link ClientStartableFlow}.
     *
     * @return The request body.
     */
    @NotNull
    String getRequestBody();

    /**
     * Gets the request body and deserializes it into the given type, using a {@link MarshallingService}.
     * <p>
     * The selected {@link MarshallingService} will determine what format data is returned.
     *
     * @param marshallingService The {@link MarshallingService} to use to deserialize this request body.
     * @param clazz The class to deserialize the data into.
     *
     * @return An instance of the class populated by the provided input data.
     */
    @NotNull
    <T> T getRequestBodyAs(@NotNull MarshallingService marshallingService, @NotNull Class<T> clazz);

    /**
     * Gets the request body and deserializes it into a list of the given type, using a {@link MarshallingService}.
     * <p>
     * The selected {@link MarshallingService} will determine what format data is returned.
     *
     * @param marshallingService The {@link MarshallingService} to use to deserialize this request body.
     * @param clazz The class to deserialize the data into.
     *
     * @return A list of instances of the class populated by the provided input data.
     */
    @NotNull
    <T> List<T> getRequestBodyAsList(@NotNull MarshallingService marshallingService, @NotNull Class<T> clazz);

    /**
     * Gets the request body and deserializes it into a map of the given value type, using a {@link MarshallingService}.
     * <p>
     * The selected {@link MarshallingService} will determine what format data is returned.
     *
     * @param marshallingService The {@link MarshallingService} to use to deserialize this request body.
     * @param keyClass The class to deserialize the key data into.
     * @param valueClass The class to deserialize the value data into.
     *
     * @return A map of instances of the value class populated by the provided input data.
     */
    @NotNull
    <K, V> Map<K, V> getRequestBodyAsMap(
        @NotNull MarshallingService marshallingService,
        @NotNull Class<K> keyClass,
        @NotNull Class<V> valueClass
    );
}
