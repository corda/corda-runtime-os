package net.corda.flow.manager

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.state.Checkpoint
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.application.flows.Flow

/**
 * The FlowStackService provides an API for managing the flow/sub-flow call stack, stored in the [Checkpoint] state.
 */
interface FlowStackService : NonSerializable {

    val size: Int

    /**
     * Pushes a flow onto the stack
     *
     * @param flow the flow to be pushed onto the stack
     * @return the [FlowStackItem] created
     */
    fun push(flow: Flow<*>): FlowStackItem

    /**
     * Finds the nearest matching [FlowStackItem] to the top of the stack that matches the predicate
     *
     * @param predicate matching function
     * @return the first matching [FlowStackItem] or null if nothing matched.
     */
    fun nearestFirst(predicate: (FlowStackItem) -> Boolean): FlowStackItem?

    /**
     * Returns the item at the top of the stack without removing it.
     *
     * @return the [FlowStackItem] at the top of the stack of null if the stack is empty
     */
    fun peek(): FlowStackItem?

    /**
     * Removes and returns the [FlowStackItem] at the top of the stack
     *
     * @return the [FlowStackItem] at the top of the stack of null if the stack is empty
     */
    fun pop(): FlowStackItem?
}