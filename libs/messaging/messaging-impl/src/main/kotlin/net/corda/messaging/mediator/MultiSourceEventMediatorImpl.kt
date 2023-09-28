package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.taskmanager.TaskManager

// TODO This will be implemented with CORE-15754
@Suppress("LongParameterList", "unused_parameter")
class MultiSourceEventMediatorImpl<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    serializer: CordaAvroSerializer<Any>,
    stateDeserializer: CordaAvroDeserializer<S>,
    private val stateManager: StateManager,
    private val taskManager: TaskManager,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
): MultiSourceEventMediator<K, S, E> {
    override val subscriptionName: LifecycleCoordinatorName
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}