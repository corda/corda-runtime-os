package net.corda.flow.state.impl

import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.state.FlowStack
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import org.bouncycastle.asn1.x500.style.RFC4519Style.c

class FlowStackImpl(
    val flowStackItems: MutableList<FlowStackItem>,
    private val initialContextPlatformProperties: Map<String, String>,
    private val initialContextUserProperties: Map<String, String>
) : FlowStack {

    override val size: Int get() = flowStackItems.size

    override fun push(flow: Flow): FlowStackItem {
        // If the stack is empty, the context properties need initialising with the values the flow was started with
        val contextUserProperties = if (flowStackItems.isEmpty()) {
            initialContextUserProperties.toMutableMap()
        } else {
            mutableMapOf()
        }
        val contextPlatformProperties = if (flowStackItems.isEmpty()) {
            initialContextPlatformProperties.toMutableMap()
        } else {
            mutableMapOf()
        }

        val stackItem =
            FlowStackItem(
                flow::class.java.name,
                flow::class.java.getIsInitiatingFlow(),
                mutableListOf(),
                contextUserProperties,
                contextPlatformProperties
            )

        flowStackItems.add(stackItem)
        return stackItem
    }

    override fun nearestFirst(predicate: (FlowStackItem) -> Boolean): FlowStackItem? {
        return flowStackItems.reversed().firstOrNull(predicate)
    }

    override fun peek(): FlowStackItem? {
        return flowStackItems.lastOrNull()
    }

    override fun peekFirst(): FlowStackItem {
        val firstItem = flowStackItems.firstOrNull()
        return checkNotNull(firstItem) { "peekFirst() was called on an empty stack." }
    }

    override fun pop(): FlowStackItem? {
        if (flowStackItems.size == 0) {
            return null
        }
        val stackEntry = flowStackItems.last()
        flowStackItems.removeLast()
        return stackEntry
    }

    private fun Class<*>.getIsInitiatingFlow(): Boolean {
        return this.getDeclaredAnnotation(InitiatingFlow::class.java) != null
    }
}