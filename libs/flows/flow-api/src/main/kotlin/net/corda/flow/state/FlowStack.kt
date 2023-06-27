package net.corda.flow.state

import net.corda.data.KeyValuePairList
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.pipeline.metrics.FlowMetrics
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.application.flows.Flow

/**
 * The FlowStack provides an API for managing the flow/sub-flow call stack, stored in the checkpoint state.
 */
interface FlowStack : NonSerializable {

    val size: Int

    /**
     * @return Return true when a flow stack has no [FlowStackItem]s
     */
    fun isEmpty() = size == 0

    /**
     * Pushes a flow onto the stack with some initial context properties. These context properties form an initial set
     * for this stack item and thus behave just like any other context properties added at a particular point in the
     * context stack. Initial context properties for a Flow can be added by calling this method for the first item
     * added to its [FlowStack].
     *
     * @param flow the flow to be pushed onto the stack
     * @param contextUserProperties the user context properties to add to this stack item
     * @param contextPlatformProperties the platform context properties to add to this stack item
     * @return the [FlowStackItem] created
     */
    fun pushWithContext(
        flow: Flow,
        contextUserProperties: KeyValuePairList,
        contextPlatformProperties: KeyValuePairList,
        flowMetrics: FlowMetrics
    ): FlowStackItem

    /**
     * Pushes a flow onto the stack
     *
     * @param flow the flow to be pushed onto the stack
     * @return the [FlowStackItem] created
     */
    fun push(flow: Flow, flowMetrics: FlowMetrics): FlowStackItem

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
     * @return the [FlowStackItem] at the top of the stack or null if the stack is empty
     */
    fun peek(): FlowStackItem?

    /**
     * Returns the first item of the stack without removing it.
     *
     * @return the first [FlowStackItem] on the stack or null if the stack is empty
     */
    fun peekFirst(): FlowStackItem?

    /**
     * Removes and returns the [FlowStackItem] at the top of the stack
     *
     * @return the [FlowStackItem] at the top of the stack of null if the stack is empty
     */
    fun pop(): FlowStackItem?
}