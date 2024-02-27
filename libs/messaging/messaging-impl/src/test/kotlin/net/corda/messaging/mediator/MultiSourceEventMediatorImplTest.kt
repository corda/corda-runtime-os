package net.corda.messaging.mediator

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.mediator.factory.MediatorComponentFactory
import net.corda.messaging.mediator.processor.ConsumerProcessor
import net.corda.taskmanager.TaskManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class MultiSourceEventMediatorImplTest {

    private val mediatorSubscriptionState = spy(MediatorSubscriptionState(AtomicBoolean(false), AtomicBoolean(false)))
    private val messagingClient = mock<MessagingClient>()
    private val consumerProcessor = mock<ConsumerProcessor<Any, Any, Any>>()
    private val mediatorComponentFactory = mock<MediatorComponentFactory<Any, Any, Any>>().apply {
        whenever(createClients(any())).thenReturn(listOf(messagingClient))
        whenever(createRouter(any())).thenReturn(mock())
        whenever(createConsumerProcessor(any(), eq(mediatorSubscriptionState), any(), any())).thenReturn(consumerProcessor)
    }
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val taskManager = mock<TaskManager>().apply {
        whenever(executeLongRunningTask(any<() -> Any>())).thenAnswer { invocation ->
            val command = invocation.getArgument<() -> Any>(0)
            CompletableFuture.supplyAsync(command)
        }

        whenever(executeShortRunningTask(any<() -> Any>())).thenAnswer { invocation ->
            val command = invocation.getArgument<() -> Any>(0)
            CompletableFuture.supplyAsync(command)
        }
    }
    private val messageProcessorMock = mock<StateAndEventProcessor<Any, Any, Any>>().apply {
        whenever(keyClass).thenReturn(Any::class.java)
        whenever(eventValueClass).thenReturn(Any::class.java)
        whenever(stateValueClass).thenReturn(Any::class.java)
    }
    private val eventMediatorConfig = mock<EventMediatorConfig<Any, Any, Any>>().apply {
        whenever(name).thenReturn("name")
        whenever(stateManager).thenReturn(mock())
        whenever(messageProcessor).thenReturn(messageProcessorMock)
        whenever(consumerFactories).thenReturn(listOf(mock(), mock()))
    }

    private lateinit var mediator: MultiSourceEventMediator<Any, Any, Any>

    @BeforeEach
    fun setup(){
        mediator = MultiSourceEventMediatorImpl(eventMediatorConfig, taskManager, mediatorComponentFactory, lifecycleCoordinator)
    }

    @Test
    @Disabled
    fun `Start And close the mediator`() {
        mediator.start()
        Thread.sleep(30)

        verify(lifecycleCoordinator).start()
        verify(mediatorComponentFactory).createClients(any())
        verify(mediatorComponentFactory).createRouter(any())
        verify(consumerProcessor, times(2)).processTopic(any(), any())
        verify(taskManager, times(3)).executeLongRunningTask<Unit>(any())

        mediator.close()
        verify(lifecycleCoordinator).close()
        verify(mediatorSubscriptionState).stop()
    }
}
