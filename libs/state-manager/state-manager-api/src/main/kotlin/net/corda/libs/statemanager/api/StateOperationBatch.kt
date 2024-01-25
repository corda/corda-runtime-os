package net.corda.libs.statemanager.api

/**
 * A batch of operations to be executed together.
 *
 * The state manager group all updates in the same batch in a single context when communicating with the backend. If the
 * underlying storage mechanism uses transactions for example, it will group all operations provided in the same
 * transaction.
 *
 * Attempting to add a new state with the same key as one already in the group will result in an error being thrown.
 *
 * Attempting to execute the same batch twice, or add a new state to a batch that has previously been executed, will
 * result in an error.
 *
 * Implementations of this interface are not thread safe and should only be accessed from a single thread.
 */
interface StateOperationBatch {

    /**
     * Add a collection of states to be created as part of this batch.
     *
     * The batch is not copied on return, and so it is safe to reuse the existing object if desired.
     *
     * @param states The collection of states to be created.
     * @return The batch, to which new state operations can be added.
     * @throws IllegalArgumentException if a state is added with a key that is already part of the batch.
     * @throws IllegalStateException if the batch has already been executed.
     */
    fun create(states: Collection<State>): StateOperationBatch

    /**
     * Add a single state to be created as part of this batch.
     *
     * The batch is not copied on return, and so it is safe to reuse the existing object if desired.
     *
     * @param state The state to be created.
     * @return The batch, to which new state operations can be added.
     * @throws IllegalArgumentException if a state is added with a key that is already part of the batch.
     * @throws IllegalStateException if the batch has already been executed.
     */
    fun create(state: State): StateOperationBatch = create(listOf(state))

    /**
     * Add a collection of states to be updated as part of this batch.
     *
     * The batch is not copied on return, and so it is safe to reuse the existing object if desired.
     *
     * @param states The collection of states to be updated.
     * @return The batch, to which new state operations can be added.
     * @throws IllegalArgumentException if a state is added with a key that is already part of the batch.
     * @throws IllegalStateException if the batch has already been executed.
     */
    fun update(states: Collection<State>): StateOperationBatch

    /**
     * Add a single state to be updated as part of this batch.
     *
     * The batch is not copied on return, and so it is safe to reuse the existing object if desired.
     *
     * @param state The state to be updated.
     * @return The batch, to which new state operations can be added.
     * @throws IllegalArgumentException if a state is added with a key that is already part of the batch.
     * @throws IllegalStateException if the batch has already been executed.
     */
    fun update(state: State): StateOperationBatch = update(listOf(state))

    /**
     * Add a collection of states to be deleted as part of this batch.
     *
     * The batch is not copied on return, and so it is safe to reuse the existing object if desired.
     *
     * @param states The collection of states to be deleted.
     * @return The batch, to which new state operations can be added.
     * @throws IllegalArgumentException if a state is added with a key that is already part of the batch.
     * @throws IllegalStateException if the batch has already been executed.
     */
    fun delete(states: Collection<State>): StateOperationBatch

    /**
     * Add a state to be deleted as part of this batch.
     *
     * The batch is not copied on return, and so it is safe to reuse the existing object if desired.
     *
     * @param state The state to be deleted.
     * @return The batch, to which new state operations can be added.
     * @throws IllegalArgumentException if a state is added with a key that is already part of the batch.
     * @throws IllegalStateException if the batch has already been executed.
     */
    fun delete(state: State): StateOperationBatch = delete(listOf(state))

    /**
     * Execute all operations requested as part of this batch.
     *
     * All operations are performed with best effort semantics. Any operation that can succeed will do so, and failures
     * are reported back to the user.
     *
     * Calling this function invalidates the batch, and any further attempts to add states or invoke execute again will
     * result in failure. If further state processing is required, a new batch should be constructed from a state
     * manager instance.
     *
     * @return A map of keys to the current state for any key that failed, or null if no state exists. States that
     *         failed to be deleted due to the state never existing will not appear here. Updates that failed due to the
     *         state not existing will appear with a null value.
     */
    fun execute(): Map<String, State?>
}
