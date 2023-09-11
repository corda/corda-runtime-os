package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.EventMediatorFactory
import net.corda.messaging.api.mediator.statemanager.StateManager
import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.mediator.MultiSourceEventMediatorImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [EventMediatorFactory::class])
class EventMediatorFactoryImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = TaskManager::class)
    private val taskManager: TaskManager,
    @Reference(service = StateManager::class)
    private val stateManager: StateManager,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
): EventMediatorFactory {

    override fun <K : Any, S : Any, E : Any> createMultiSourceEventMediator(
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

//        val msgConfig = messagingConfig.withFallback(defaults)
//        messagingConfig.getInt(BootConfig.INSTANCE_ID),
//        Duration.ofMillis(messagingConfig.getLong(MessagingConfig.Subscription.POLL_TIMEOUT)),
//        Duration.ofMillis(messagingConfig.getLong(MessagingConfig.Subscription.THREAD_STOP_TIMEOUT)),
//        messagingConfig.getInt(MessagingConfig.Subscription.PROCESSOR_RETRIES),
//        messagingConfig.getInt(MessagingConfig.Subscription.SUBSCRIBE_RETRIES),
//        messagingConfig.getInt(MessagingConfig.Subscription.COMMIT_RETRIES),
//        Duration.ofMillis(messagingConfig.getLong(MessagingConfig.Subscription.PROCESSOR_TIMEOUT)),
//        messagingConfig
}