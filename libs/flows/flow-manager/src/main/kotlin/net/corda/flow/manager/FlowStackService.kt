package net.corda.flow.manager

import net.corda.data.flow.FlowStackItem
import net.corda.serialization.NonSerializable
import net.corda.v5.application.flows.Flow

interface FlowStackService : NonSerializable {
    fun push(flow: Flow<*>): FlowStackItem

    fun nearestFirst(predicate: (FlowStackItem) -> Boolean): FlowStackItem?

    fun peek(): FlowStackItem?

    fun pop(): FlowStackItem?
}