package net.corda.utxo.token.sync.integration.tests.fakes

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener

class StateAndEventSubscriptionFake<K : Any, S : Any, E : Any>(
    val subscriptionConfig: SubscriptionConfig,
    val processor: StateAndEventProcessor<K, S, E>,
    val stateAndEventListener: StateAndEventListener<K, S>?,
    val publishedRecords: MutableMap<String,MutableList<Record<*, *>>>
) : StateAndEventSubscription<K, S, E> {

    private val states = mutableMapOf<K, S>()

    fun clearState() {
        states.clear()
    }

    fun getState(): Map<K,S>{
        return states
    }

    fun publish(key: K, event: Record<K, E>) {
        val result = processor.onNext(states[key], event)

        check(!result.markForDLQ) { "The event ${event} caused a DLQ" }

        if (result.updatedState == null) {
            states.remove(key)
        } else {
            states[key] = result.updatedState!!
        }

        result.responseEvents.forEach {
            val recordsList = publishedRecords.computeIfAbsent(it.topic) { mutableListOf() }
            recordsList.add(it)
        }
    }

    fun publishState(key: K, newState:S){
        states[key] = newState
    }

    override fun close() {
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName(subscriptionConfig.groupName)

    override fun start() {
    }
}