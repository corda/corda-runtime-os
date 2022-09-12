package net.corda.flow.state.impl

import net.corda.data.KeyValuePairList
import net.corda.flow.state.FlowStack
import net.corda.flow.state.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItem as AvroFlowStackItem
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.flow.utils.keyValuePairListOf
import net.corda.flow.utils.toMutableMap
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow

/**
 * Even though the [FlowStackImpl] works with both types of the [FlowStackItem] internally (Avro and non Avro
 * serializable), only the non-Avro-serializable version is exposed so Avro generated types don't end in the stack
 * of an executing flow.
 */
class FlowStackImpl(stackItems: MutableList<AvroFlowStackItem>) : FlowStack {
    val flowStackItems = stackItems.map {
        FlowStackItem(
            it.flowName,
            it.isInitiatingFlow,
            it.sessionIds.toMutableList(),
            it.contextUserProperties.toMutableMap(),
            it.contextPlatformProperties.toMutableMap()
        )
    }.toMutableList()

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
                contextUserProperties.toMutableMap(),
                contextPlatformProperties.toMutableMap(),
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

    override fun peekFirst(): FlowStackItem? {
        return flowStackItems.firstOrNull()
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

    /**
     * Transforms the current list of kryo serializable items into a list of avro serializable items.
     *
     * @return the list of avro serializable [FlowStackItem]s
     */
    fun toAvro(): List<AvroFlowStackItem> {
        return flowStackItems.map {
            AvroFlowStackItem.newBuilder()
                .setFlowName(it.flowName)
                .setIsInitiatingFlow(it.isInitiatingFlow)
                .setSessionIds(it.sessionIds.toList())
                .setContextUserProperties(keyValuePairListOf(it.contextUserProperties))
                .setContextPlatformProperties(keyValuePairListOf(it.contextPlatformProperties))
                .build()
        }.toMutableList()
    }
}
