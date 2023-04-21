package net.corda.v5.application.persistence;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Used to build a Query that supports parameters.
 *
 * @param <R> the type of the results.
 */
public interface ParameterizedQuery<R> extends PagedQuery<R> {

    /**
     * Sets the maximum number of results to return.
     * <p>
     * If no limit is set, all records will be returned.
     *
     * @param limit The maximum number of results to return.
     *
     * @return The same {@link ParameterizedQuery} instance.
     *
     * @throws IllegalArgumentException If {@code limit} is negative.
     *
     * @see PagedQuery#setLimit
     */
    @Override
    @NotNull
    ParameterizedQuery<R> setLimit(int limit);

    /**
     * Sets the index of the first result in the query to return.
     * <p>
     * A default of `0` will be used if it is not set.
     *
     * @param offset The index of the first result in the query to return.
     *
     * @return The same {@link ParameterizedQuery} instance.
     *
     * @throws IllegalArgumentException If {@code offset} is negative.
     *
     * @see PagedQuery#setOffset
     */
    @Override
    @NotNull
    ParameterizedQuery<R> setOffset(int offset);

    /**
     * Set parameter with given [name].
     *
     * @param name The name of the parameter in the {@link ParameterizedQuery}.
     * @param value The value of the parameter to use in the {@link ParameterizedQuery}.
     *
     * @return the same {@link ParameterizedQuery} instance.
     */
    @NotNull
    ParameterizedQuery<R> setParameter(@NotNull String name, @NotNull Object value);

    /**
     * Sets the parameters as a {@link Map}.
     *
     * @param parameters To parameters be used in the {@link ParameterizedQuery}
     *
     * @return The same {@link ParameterizedQuery} instance.
     */
    @NotNull
    ParameterizedQuery<R> setParameters(@NotNull Map<String, Object> parameters);
}
