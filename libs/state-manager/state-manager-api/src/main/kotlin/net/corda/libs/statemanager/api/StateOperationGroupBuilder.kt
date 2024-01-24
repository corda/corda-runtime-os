package net.corda.libs.statemanager.api

/**
 * A group of operations to be executed together as part of the same batch to the state manager.
 *
 * The state manager will attempt to group all updates in the same group together. If the underlying storage mechanism
 * uses transactions for example, it will group all operations provided in the same transaction.
 *
 * Attempting to add a new state
 */
interface StateOperationGroupBuilder {

    /**
     * Add a collection of states to be created as part of this state operations group.
     *
     * The builder is not copied on return, and so it is safe to reuse the existing builder if desired.
     *
     * @param states The collection of states to be created.
     * @return The builder, to which new state operations can be added.
     */
    fun create(states: Collection<State>) : StateOperationGroupBuilder

    /**
     * Add a single state to be created as part of this operations group.
     *
     * The builder is not copied on return, and so it is safe to reuse the existing builder if desired.
     *
     * @param state The state to be created.
     * @return The builder, to which new state operations can be added.
     */
    fun create(state: State) : StateOperationGroupBuilder = create(listOf(state))

    /**
     * Add a collection of states to be updated as part of this state operations group.
     *
     * The builder is not copied on return, and so it is safe to reuse the existing builder if desired.
     *
     * @param states The collection of states to be updated.
     * @return The builder, to which new state operations can be added.
     */
    fun update(states: Collection<State>) : StateOperationGroupBuilder

    /**
     * Add a single state to be updated as part of this state operations group.
     *
     * The builder is not copied on return, and so it is safe to reuse the existing builder if desired.
     *
     * @param state The state to be updated.
     * @return The builder, to which new state operations can be added.
     */
    fun update(state: State) : StateOperationGroupBuilder = update(listOf(state))

    /**
     * Add a collection of states to be deleted as part of this state operations group.
     *
     * The builder is not copied on return, and so it is safe to reuse the existing builder if desired.
     *
     * @param states The collection of states to be deleted.
     * @return The builder, to which new state operations can be added.
     */
    fun delete(states: Collection<State>) : StateOperationGroupBuilder

    /**
     * Add a state to be deleted as part of this state operations group.
     *
     * The builder is not copied on return, and so it is safe to reuse the existing builder if desired.
     *
     * @param state The state to be deleted.
     * @return The builder, to which new state operations can be added.
     */
    fun delete(state: State) : StateOperationGroupBuilder = delete(listOf(state))

    /**
     * Execute all operations requested as part of this state operations group.
     *
     * All operations are performed with best effort semantics. Any operation that can succeed will do so, and failures
     * are reported back to the user.
     *
     * Calling this function invalidates the operations group, and any further attempts to add states or invoke execute
     * again will result in failure. If further state processing is required, a new operations group should be
     * constructed from a state manager instance.
     *
     * @return A map of keys to the current state for any key that failed, or null if no state exists.
     */
    fun execute() : Map<String, State?>
}