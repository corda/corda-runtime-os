package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.taskmanager.TaskManager
import net.corda.taskmanager.TaskManagerFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MultiSourceEventMediatorFactoryTest {
    private lateinit var multiSourceEventMediatorFactory: MultiSourceEventMediatorFactoryImpl
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val serializer = mock<CordaAvroSerializer<Any>>()
    private val stateDeserializer = mock<CordaAvroDeserializer<Any>>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val taskManagerFactory = mock<TaskManagerFactory>()

    @BeforeEach
    fun beforeEach() {
        doReturn(serializer).`when`(cordaAvroSerializationFactory).createAvroSerializer<Any>(anyOrNull())
        doReturn(stateDeserializer).`when`(cordaAvroSerializationFactory).createAvroDeserializer(any(), any<Class<Any>>())
        doReturn(mock<TaskManager>()).`when`(taskManagerFactory).createThreadPoolTaskManager(any(), any(), any())
        doReturn(mock<LifecycleCoordinator>()).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
        multiSourceEventMediatorFactory = MultiSourceEventMediatorFactoryImpl(
            cordaAvroSerializationFactory,
            lifecycleCoordinatorFactory,
            taskManagerFactory,
        )
    }

    @Test
    fun testCreateMultiSourceEventMediator() {
        val messageProcessor = mock<StateAndEventProcessor<Any, Any, Any>>()
        doReturn(Any::class.java).`when`(messageProcessor).stateValueClass
        val messageRouterFactory = mock<MessageRouterFactory>()
        val stateManager = mock<StateManager>()
        val config = mock<EventMediatorConfig<Any, Any, Any>>()
        doReturn(messageProcessor).`when`(config).messageProcessor
        doReturn(String::class.java).`when`(messageProcessor).keyClass
        doReturn(String::class.java).`when`(messageProcessor).eventValueClass
        doReturn(String::class.java).`when`(messageProcessor).stateValueClass
        doReturn(messageRouterFactory).`when`(config).messageRouterFactory
        doReturn("name").`when`(config).name
        doReturn(1).`when`(config).threads
        doReturn("name").`when`(config).threadName
        doReturn(stateManager).`when`(config).stateManager

        val mediator = multiSourceEventMediatorFactory.create(config)

        Assertions.assertNotNull(mediator)
    }
}