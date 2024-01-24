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
     * States to be created as part of this state operations group.
     *
     * @param states The collection of states to be created.
     * @return The builder, to which new state operations can be added.
     */
    fun create(states: Collection<State>) : StateOperationGroupBuilder

    /**
     * States to be updated as part of this state operations group.
     *
     * @param states The collection of states to be updated.
     * @return The builder, to which new state operations can be added.
     */
    fun update(states: Collection<State>) : StateOperationGroupBuilder

    /**
     * States to be deleted as part of this state operations group.
     *
     * @param states The collection of states to be deleted.
     * @return The builder, to which new state operations can be added.
     */
    fun delete(states: Collection<State>) : StateOperationGroupBuilder

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