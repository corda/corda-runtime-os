package net.corda.messaging.mediator.factory

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.api.processor.StateAndEventProcessor
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

    @BeforeEach
    fun beforeEach() {
        doReturn(serializer).`when`(cordaAvroSerializationFactory).createAvroSerializer<Any>(anyOrNull())
        doReturn(stateDeserializer).`when`(cordaAvroSerializationFactory).createAvroDeserializer(any(), any<Class<Any>>())
        multiSourceEventMediatorFactory = MultiSourceEventMediatorFactoryImpl(
            cordaAvroSerializationFactory,
            mock<TaskManager>(),
            mock<StateManager>(),
            mock<LifecycleCoordinatorFactory>(),
        )
    }

    @Test
    fun testCreateMultiSourceEventMediator() {
        val messageProcessor = mock<StateAndEventProcessor<Any, Any, Any>>()
        doReturn(Any::class.java).`when`(messageProcessor).stateValueClass
        val messageRouterFactory = mock<MessageRouterFactory>()
        val config = mock<EventMediatorConfig<Any, Any, Any>>()
        doReturn(messageProcessor).`when`(config).messageProcessor
        doReturn(messageRouterFactory).`when`(config).messageRouterFactory

        val mediator = multiSourceEventMediatorFactory.create(config)

        Assertions.assertNotNull(mediator)
    }
}