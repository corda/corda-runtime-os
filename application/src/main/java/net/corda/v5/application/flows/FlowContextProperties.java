package net.corda.v5.application.flows;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context properties of a {@link Flow} are key value pairs of Strings. They are comprised of two sets of properties, those
 * set by the platform when a {@link Flow} is started, and those which are added by the CorDapp developer during the execution
 * of their flow. The latter set are referred to as user context properties. All context properties are immutable once
 * set.
 */
public interface FlowContextProperties {
    /**
     * A constant containing the Corda reserved prefix.
     */
    String CORDA_RESERVED_PREFIX = "corda."; // constant must be lowercase

    /**
     * Puts a user value into the context property store.
     * <p>
     * Setting user properties with the same key as existing platform properties throws an {@link IllegalArgumentException}.
     * <p>
     * It is highly advisable to scope user property keys with some unique prefix, e.g. package name. Corda's platform
     * keys are usually prefixed with {@link #CORDA_RESERVED_PREFIX}. This is reserved and an attempt to prefix a user key with
     * {@link #CORDA_RESERVED_PREFIX} also throws an {@link IllegalArgumentException}, whether the key exists already or not.
     * <p>
     * Both sets of context properties are propagated automatically from the originating [Flow] to all sub-flows
     * initiated flows, and services. Where sub-flows and initiated flows have extra user properties added, these are
     * only visible in the scope of those flows and any of their sub-flows, initiated flows or services, but not back up
     * the flow stack to any flows which launched or initiated those flows. The same is true of overwritten user
     * properties. These properties are overwritten for the current flow and any flows initiated or instantiated by that
     * flow, and also any further down the chain. When execution returns to a flow higher up the stack (a parent or one
     * that initiated it) the overwritten properties are not visible.
     *
     * @param key The property key.
     * @param value The property value.
     *
     * @throws IllegalArgumentException If a platform property already exists for this key or if the key is prefixed by
     * {@link #CORDA_RESERVED_PREFIX}.
     */
    void put(@NotNull String key, @NotNull String value);

    /**
     * Gets a value from the context property store.
     *
     * @param key The property key.
     *
     * @return The property value.
     */
    @Nullable
    String get(@NotNull String key);
}
