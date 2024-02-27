package net.corda.uniqueness.checker.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BatchedUniquenessCheckerLifecycleTests {
    @Test
    fun `when RegistrationStatusChangeEvent UP register endpoint and start`() {
        val coordinator = mock<LifecycleCoordinator>()
        doAnswer { (it.getArgument(1) as () -> RPCSubscription<UniquenessCheckRequestAvro, FlowEvent>).invoke() }
            .whenever(coordinator)
            .createManagedResource(any(), any<() -> RPCSubscription<UniquenessCheckRequestAvro, FlowEvent>>())
        val eventHandlerCaptor = argumentCaptor<LifecycleEventHandler>()
        val coordinatorFactory = mock<LifecycleCoordinatorFactory>() {
            on { createCoordinator(any(), eventHandlerCaptor.capture()) } doReturn (coordinator)
        }
        val subscription = mock<RPCSubscription<UniquenessCheckRequestAvro, FlowEvent>>()
        val subscriptionFactory = mock<SubscriptionFactory>() {
            on { createHttpRPCSubscription(any(), any<UniquenessCheckMessageProcessor>()) } doReturn subscription
        }
        BatchedUniquenessCheckerLifecycleImpl(
            coordinatorFactory,
            subscriptionFactory,
            mock(),
            mock(),
            mock(),
        )

        eventHandlerCaptor.firstValue
            .processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(subscriptionFactory).createHttpRPCSubscription(
            argThat { config: SyncRPCConfig ->
                config.endpoint == "/uniqueness-checker"

            },
            isA<UniquenessCheckMessageProcessor>()
        )
        verify(subscription).start()
    }
}