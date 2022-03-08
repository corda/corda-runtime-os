package net.corda.flow.manager.impl

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.FlowStackService
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow

class FlowStackServiceImpl(checkpoint: Checkpoint) : FlowStackService {

    private val flowStackItems: MutableList<FlowStackItem>

    init {
        /**
         * There is no guarantee the state of the stack, so
         * we initialise all lists if they are null
         */
        if (checkpoint.flowStackItems == null) {
            checkpoint.flowStackItems = mutableListOf()
        }

        flowStackItems = checkpoint.flowStackItems

        for (flowStackItem in flowStackItems) {
            if (flowStackItem.sessionIds == null) {
                flowStackItem.sessionIds = mutableListOf()
            }
        }
    }

    override val size: Int get() = flowStackItems.size

    override fun push(flow: Flow<*>): FlowStackItem {
        val stackItem = FlowStackItem(flow.javaClass.name, flow.getIsInitiatingFlow(), mutableListOf())
        flowStackItems.add(stackItem)
        return stackItem
    }

    override fun nearestFirst(predicate: (FlowStackItem) -> Boolean): FlowStackItem? {
        return flowStackItems.reversed().firstOrNull(predicate)
    }

    override fun peek(): FlowStackItem? {
        return flowStackItems.lastOrNull()
    }

    override fun pop(): FlowStackItem? {
        if (flowStackItems.size == 0) {
            return null
        }
        val stackEntry = flowStackItems.last()
        flowStackItems.removeLast()
        return stackEntry
    }

    private fun Flow<*>.getIsInitiatingFlow(): Boolean {
        var current: Class<in Flow<*>> = this.javaClass

        while (true) {
            val annotation = current.getDeclaredAnnotation(InitiatingFlow::class.java)
            if (annotation != null) {
                require(annotation.version > 0) { "Flow versions have to be greater or equal to 1" }
                return true
            }

            current = current.superclass
                ?: return false
        }
    }
}