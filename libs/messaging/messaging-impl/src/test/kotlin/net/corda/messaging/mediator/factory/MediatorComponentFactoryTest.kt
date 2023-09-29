package net.corda.messaging.mediator.factory

import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFinder
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MediatorComponentFactoryTest {
    private lateinit var mediatorComponentFactory: MediatorComponentFactory<String, String, String>
    private val messageProcessor = object : StateAndEventProcessor<String, String, String> {
        override fun onNext(state: String?, event: Record<String, String>): StateAndEventProcessor.Response<String> {
            TODO("Not yet implemented")
        }
        override val keyClass get() = String::class.java
        override val stateValueClass get() = String::class.java
        override val eventValueClass get() = String::class.java

    }
    private val consumerFactories = listOf(
        mock<MediatorConsumerFactory>(),
        mock<MediatorConsumerFactory>(),
    )
    private val clientFactories = listOf(
        mock<MessagingClientFactory>(),
        mock<MessagingClientFactory>(),
    )
    private val messageRouterFactory = mock<MessageRouterFactory>()

    @BeforeEach
    fun beforeEach() {
        consumerFactories.forEach {
            doReturn(mock<MediatorConsumer<String, String>>()).`when`(it).create(
                any<MediatorConsumerConfig<String, String>>()
            )
        }

        clientFactories.forEach {
            doReturn(mock<MessagingClient>()).`when`(it).create(
                any<MessagingClientConfig>()
            )
        }

        doReturn(mock<MessageRouter>()).`when`(messageRouterFactory).create(
            any<MessagingClientFinder>()
        )

        mediatorComponentFactory = MediatorComponentFactory(
            messageProcessor,
            consumerFactories,
            clientFactories,
            messageRouterFactory,
        )
    }

    @Test
    fun `successfully creates consumers`() {
        val onSerializationError: (ByteArray) -> Unit = {}

        val mediatorConsumers = mediatorComponentFactory.createConsumers(onSerializationError)

        assertEquals(consumerFactories.size, mediatorConsumers.size)
        mediatorConsumers.forEach {
            assertNotNull(it)
        }

        consumerFactories.forEach {
            val consumerConfigCaptor = argumentCaptor<MediatorConsumerConfig<String, String>>()
            verify(it).create(consumerConfigCaptor.capture())
            val consumerConfig = consumerConfigCaptor.firstValue
            assertEquals(String::class.java, consumerConfig.keyClass)
            assertEquals(String::class.java, consumerConfig.valueClass)
            assertEquals(onSerializationError, consumerConfig.onSerializationError)
        }
    }

    @Test
    fun `throws exception when consumer factory not provided`() {
        val mediatorComponentFactory = MediatorComponentFactory(
            messageProcessor,
            emptyList(),
            clientFactories,
            messageRouterFactory,
        )

        assertThrows<IllegalStateException> {
            mediatorComponentFactory.createConsumers { }
        }
    }

    @Test
    fun `successfully creates clients`() {
        val onSerializationError: (ByteArray) -> Unit = {}

        val mediatorClients = mediatorComponentFactory.createClients(onSerializationError)

        assertEquals(clientFactories.size, mediatorClients.size)
        mediatorClients.forEach {
            assertNotNull(it)
        }

        clientFactories.forEach {
            val clientConfigCaptor = argumentCaptor<MessagingClientConfig>()
            verify(it).create(clientConfigCaptor.capture())
            val clientConfig = clientConfigCaptor.firstValue
            assertEquals(onSerializationError, clientConfig.onSerializationError)
        }
    }

    @Test
    fun `throws exception when client factory not provided`() {
        val mediatorComponentFactory = MediatorComponentFactory(
            messageProcessor,
            consumerFactories,
            emptyList(),
            messageRouterFactory,
        )

        assertThrows<IllegalStateException> {
            mediatorComponentFactory.createClients { }
        }
    }

    @Test
    fun `successfully creates message router`() {
        val clients = listOf(
            mock<MessagingClient>(),
            mock<MessagingClient>(),
        )
        clients.forEachIndexed { id, client ->
            Mockito.doReturn(id.toString()).whenever(client).id
        }

        val messageRouter = mediatorComponentFactory.createRouter(clients)

        assertNotNull(messageRouter)

        val messagingClientFinderCaptor = argumentCaptor<MessagingClientFinder>()
        verify(messageRouterFactory).create(messagingClientFinderCaptor.capture())
        val messagingClientFinder = messagingClientFinderCaptor.firstValue

        clients.forEachIndexed { id, client ->
            assertEquals(client, messagingClientFinder.find(id.toString()))
        }
        assertThrows<IllegalStateException> {
            messagingClientFinder.find("unknownId")
        }
    }
}