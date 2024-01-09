package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.messaging.mediator.GroupAllocator
import net.corda.messaging.mediator.MultiSourceEventMediatorImpl
import net.corda.messaging.mediator.StateManagerHelper
import net.corda.taskmanager.TaskManagerFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Component(service = [MultiSourceEventMediatorFactory::class])
class MultiSourceEventMediatorFactoryImpl(
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val taskManagerFactory: TaskManagerFactory
) : MultiSourceEventMediatorFactory {

    @Activate
    constructor(
        @Reference(service = CordaAvroSerializationFactory::class) cordaAvroSerializationFactory: CordaAvroSerializationFactory,
        @Reference(service = LifecycleCoordinatorFactory::class) lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    ) : this(
        cordaAvroSerializationFactory, lifecycleCoordinatorFactory, TaskManagerFactory.INSTANCE
    )

    override fun <K : Any, S : Any, E : Any> create(
        eventMediatorConfig: EventMediatorConfig<K, S, E>,
    ): MultiSourceEventMediator<K, S, E> {
        val stateManagerHelper = createStateManagerHelper(eventMediatorConfig)
        val mediatorComponentFactory = createMediatorComponentFactory(eventMediatorConfig, stateManagerHelper)
        val lifecycleCoordinator = createLifecycleCoordinator(eventMediatorConfig)
        val taskManager = taskManagerFactory.createThreadPoolTaskManager(
            name = eventMediatorConfig.name, threadName = eventMediatorConfig.threadName, threads = eventMediatorConfig.threads
        )

        return MultiSourceEventMediatorImpl(eventMediatorConfig, taskManager, mediatorComponentFactory, lifecycleCoordinator)
    }

    private fun <E : Any, K : Any, S : Any> createMediatorComponentFactory(
        eventMediatorConfig: EventMediatorConfig<K, S, E>, stateManagerHelper: StateManagerHelper<S>
    ) = MediatorComponentFactory(
        eventMediatorConfig.messageProcessor,
        eventMediatorConfig.consumerFactories,
        eventMediatorConfig.clientFactories,
        eventMediatorConfig.messageRouterFactory,
        GroupAllocator(),
        stateManagerHelper
    )

    private fun <E : Any, K : Any, S : Any> createLifecycleCoordinator(
        eventMediatorConfig: EventMediatorConfig<K, S, E>
    ): LifecycleCoordinator {
        val uniqueId = UUID.randomUUID().toString()
        val lifecycleCoordinatorName = LifecycleCoordinatorName(
            "MultiSourceEventMediator--${eventMediatorConfig.name}", uniqueId
        )
        return lifecycleCoordinatorFactory.createCoordinator(lifecycleCoordinatorName) { _, _ -> }
    }

    private fun <E : Any, K : Any, S : Any> createStateManagerHelper(
        eventMediatorConfig: EventMediatorConfig<K, S, E>
    ): StateManagerHelper<S> {
        val stateSerializer = cordaAvroSerializationFactory.createAvroSerializer<S> { }
        val stateDeserializer = cordaAvroSerializationFactory.createAvroDeserializer(
            {}, eventMediatorConfig.messageProcessor.stateValueClass
        )
        return StateManagerHelper(
            stateSerializer, stateDeserializer
        )
    }
}