package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.messaging.api.subscription.Subscription
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.lang.IllegalArgumentException

class SubscriptionDominoTileTest {

    private val subscriptionRegistration = mock<RegistrationHandle>()
    private val childrenRegistration = mock<RegistrationHandle>()

    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
        on { followStatusChangesByName(any()) } doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val names = invocation.arguments.first() as Set<LifecycleCoordinatorName>
            when {
                names.contains(subscriptionName) -> {
                    subscriptionRegistration
                }
                names.containsAll(children.map { it.coordinatorName }) -> {
                    childrenRegistration
                }
                else -> {
                    throw IllegalArgumentException("Unexpected parameter.")
                }
            }
        }
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    private val subscription = mock<Subscription<*, *>>()
    private val subscriptionName = LifecycleCoordinatorName("sub-name", "1")

    private val children = setOf(
        mockTile(LifecycleCoordinatorName("componentA", "1")),
        mockTile(LifecycleCoordinatorName("componentB", "1")),
        mockTile(LifecycleCoordinatorName("componentC", "1"))
    )

    @Test
    fun `subscription tile starts all managed children when started`() {
        val subscriptionTile = SubscriptionDominoTile(coordinatorFactory, subscription, subscriptionName, children, children)

        subscriptionTile.start()
        children.forEach {
            verify(it, times(1)).start()
        }
    }

    @Test
    fun `subscription tile stops all managed children when stopped`() {
        val subscriptionTile = SubscriptionDominoTile(coordinatorFactory, subscription, subscriptionName, children, children)

        subscriptionTile.stop()
        children.forEach {
            verify(it, times(1)).stop()
        }
    }

    @Test
    fun `subscription tile waits for dependent children before starting the subscription`() {
        val subscriptionTile = SubscriptionDominoTile(coordinatorFactory, subscription, subscriptionName, children, children)

        subscriptionTile.start()
        verify(subscription, never()).start()

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenRegistration, LifecycleStatus.UP), coordinator)
        verify(subscription, times(1)).start()
        assertThat(subscriptionTile.state).isNotEqualTo(DominoTileState.Started)

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.UP), coordinator)
        assertThat(subscriptionTile.state).isEqualTo(DominoTileState.Started)
    }

    @Test
    fun `subscription tile goes down if any of the dependent children goes down`() {
        val subscriptionTile = SubscriptionDominoTile(coordinatorFactory, subscription, subscriptionName, children, children)

        subscriptionTile.start()
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.UP), coordinator)

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenRegistration, LifecycleStatus.DOWN), coordinator)
        verify(subscription, times(1)).stop()
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.DOWN), coordinator)
        assertThat(subscriptionTile.state).isEqualTo(DominoTileState.StoppedDueToChildStopped)
    }

    @Test
    fun `subscription tile goes down if any of the dependent children errors`() {
        val subscriptionTile = SubscriptionDominoTile(coordinatorFactory, subscription, subscriptionName, children, children)

        subscriptionTile.start()
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.UP), coordinator)

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenRegistration, LifecycleStatus.ERROR), coordinator)
        verify(subscription, times(1)).stop()
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.DOWN), coordinator)
        assertThat(subscriptionTile.state).isEqualTo(DominoTileState.StoppedDueToChildStopped)
    }

    @Test
    fun `tile errors if subscription errors`() {
        val subscriptionTile = SubscriptionDominoTile(coordinatorFactory, subscription, subscriptionName, children, children)

        subscriptionTile.start()
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.UP), coordinator)

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.ERROR), coordinator)
        assertThat(subscriptionTile.state).isEqualTo(DominoTileState.StoppedDueToError)
    }

    private fun mockTile(name: LifecycleCoordinatorName): DominoTile {
        return mock {
            on { coordinatorName } doReturn name
        }
    }

}