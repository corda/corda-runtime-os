package net.corda.flow.state.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.state.FlowStack
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.flow.utils.mutableKeyValuePairList
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow

class FlowStackImpl(val flowStackItems: MutableList<FlowStackItem>) : FlowStack {

    override val size: Int get() = flowStackItems.size

    override fun pushWithContext(
        flow: Flow,
        contextUserProperties: KeyValuePairList,
        contextPlatformProperties: KeyValuePairList,
    ): FlowStackItem {
        val stackItem =
            FlowStackItem(
                flow::class.java.name,
                flow::class.java.getIsInitiatingFlow(),
                mutableListOf(),
                mutableKeyValuePairList(contextUserProperties),
                mutableKeyValuePairList(contextPlatformProperties)
            )

        flowStackItems.add(stackItem)
        return stackItem
    }

    override fun push(flow: Flow): FlowStackItem {
        return pushWithContext(flow, emptyKeyValuePairList(), emptyKeyValuePairList())
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