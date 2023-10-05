package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.mediator.MultiSourceEventMediatorImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MultiSourceEventMediatorFactory::class])
class MultiSourceEventMediatorFactoryImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = TaskManager::class)
    private val taskManager: TaskManager,
    @Reference(service = StateManager::class)
    private val stateManager: StateManager,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
): MultiSourceEventMediatorFactory {

    override fun <K : Any, S : Any, E : Any> create(
        eventMediatorConfig: EventMediatorConfig<K, S, E>,
    ): MultiSourceEventMediator<K, S, E> {
        val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> { }
        val stateDeserializer = cordaAvroSerializationFactory.createAvroDeserializer(
            {},
            eventMediatorConfig.messageProcessor.stateValueClass
        )
        return MultiSourceEventMediatorImpl(
            eventMediatorConfig,
            serializer,
            stateDeserializer,
            stateManager,
            taskManager,
            lifecycleCoordinatorFactory,
        )
    }
}