package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.SubscriptionBase
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class SubscriptionDominoTileBaseTest {

    private val subscriptionName = LifecycleCoordinatorName("sub-name", "1")
    private val subscriptionRegistration = mock<RegistrationHandle>()
    private val childrenOneRegistration = mock<RegistrationHandle>()
    private val childrenTwoRegistration = mock<RegistrationHandle>()
    private val childrenThreeRegistration = mock<RegistrationHandle>()
    private val childOne = mockTile(LifecycleCoordinatorName("componentA", "1"))
    private val childTwo = mockTile(LifecycleCoordinatorName("componentB", "1"))
    private val childThree = mockTile(LifecycleCoordinatorName("componentC", "1"))
    private val children = setOf(childOne, childTwo, childThree)

    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { start() } doAnswer {
            handler.lastValue.processEvent(StartEvent(), mock)
        }
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
        on { followStatusChangesByName(setOf(subscriptionName)) } doReturn subscriptionRegistration
        on { followStatusChangesByName(setOf(childOne.coordinatorName)) } doReturn childrenOneRegistration
        on { followStatusChangesByName(setOf(childTwo.coordinatorName)) } doReturn childrenTwoRegistration
        on { followStatusChangesByName(setOf(childThree.coordinatorName)) } doReturn childrenThreeRegistration

        on { getManagedResource<SubscriptionBase>(SubscriptionDominoTileBase.SUBSCRIPTION) } doAnswer {
            subscription
        }
        var currentStatus: LifecycleStatus = LifecycleStatus.DOWN
        on { updateStatus(any(), any()) } doAnswer { currentStatus =  it.getArgument(0) }
        on { status } doAnswer { currentStatus }
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    private val subscriptionConfig = SubscriptionConfig("myGroup", "myEvent")
    private val subscription = mock<Subscription<*, *>>() {
        on { subscriptionName } doReturn subscriptionName
    }

    @Test
    fun `subscription tile starts all managed children when started`() {
        val subscriptionTile = SubscriptionDominoTile(
            coordinatorFactory,
            { subscription },
            subscriptionConfig,
            children.map { it.coordinatorName },
            children.map { it.toNamedLifecycle() }
        )

        subscriptionTile.start()
        children.forEach {
            verify(it, times(1)).start()
        }
    }

    @Test
    fun `subscription tile stops all managed children when stopped`() {
        val subscriptionTile = SubscriptionDominoTile(
            coordinatorFactory,
            { subscription },
            subscriptionConfig,
            children.map { it.coordinatorName },
            children.map { it.toNamedLifecycle() }
        )

        subscriptionTile.stop()
        children.forEach {
            verify(it, times(1)).stop()
        }
    }

    @Test
    fun `subscription tile waits for dependent children before starting the subscription`() {
        val subscriptionTile = SubscriptionDominoTile(
            coordinatorFactory,
            { subscription },
            subscriptionConfig,
            children.map { it.coordinatorName },
            children.map { it.toNamedLifecycle() }
        )

        subscriptionTile.start()
        verify(subscription, never()).start()

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenOneRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenTwoRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenThreeRegistration, LifecycleStatus.UP), coordinator)
        verify(subscription, times(1)).start()
        assertThat(subscriptionTile.isRunning).isFalse

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.UP), coordinator)
        assertThat(subscriptionTile.isRunning).isTrue
    }

    @Test
    fun `if there are no dependent children, subscription is started immediately`() {
        val subscriptionTile = SubscriptionDominoTile(coordinatorFactory, { subscription }, subscriptionConfig, emptySet(), emptySet())

        subscriptionTile.start()
        verify(subscription, times(1)).start()

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenOneRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenTwoRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenThreeRegistration, LifecycleStatus.UP), coordinator)
        verify(subscription, times(1)).start()
        assertThat(subscriptionTile.isRunning).isFalse
    }

    @Test
    fun `subscription tile goes down if any of the dependent children goes down`() {
        val subscriptionTile = SubscriptionDominoTile(
            coordinatorFactory,
            { subscription },
            subscriptionConfig,
            children.map { it.coordinatorName },
            children.map { it.toNamedLifecycle() }
        )

        subscriptionTile.start()
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenOneRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenTwoRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenThreeRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.UP), coordinator)

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenTwoRegistration, LifecycleStatus.DOWN), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenThreeRegistration, LifecycleStatus.DOWN), coordinator)
        verify(coordinator, atLeast(1)).closeManagedResources(setOf(SubscriptionDominoTileBase.SUBSCRIPTION))
        verify(subscriptionRegistration, times(1)).close()
        assertThat(subscriptionTile.isRunning).isFalse
    }

    @Test
    fun `subscription tile goes down if any of the dependent children errors`() {
        val subscriptionTile = SubscriptionDominoTile(
            coordinatorFactory,
            { subscription },
            subscriptionConfig,
            children.map { it.coordinatorName },
            children.map { it.toNamedLifecycle() }
        )

        subscriptionTile.start()
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenOneRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenTwoRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenThreeRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.UP), coordinator)

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenTwoRegistration, LifecycleStatus.ERROR), coordinator)
        verify(coordinator, atLeast(1)).closeManagedResources(setOf(SubscriptionDominoTileBase.SUBSCRIPTION))
        verify(subscriptionRegistration, times(1)).close()
        assertThat(subscriptionTile.isRunning).isFalse
    }

    @Test
    fun `tile errors if subscription errors`() {
        val subscriptionTile = SubscriptionDominoTile(
            coordinatorFactory,
            { subscription },
            subscriptionConfig,
            children.map { it.coordinatorName },
            children.map { it.toNamedLifecycle() }
        )

        subscriptionTile.start()
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenOneRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenTwoRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(childrenThreeRegistration, LifecycleStatus.UP), coordinator)
        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.UP), coordinator)

        handler.lastValue.processEvent(RegistrationStatusChangeEvent(subscriptionRegistration, LifecycleStatus.ERROR), coordinator)
        assertThat(subscriptionTile.status).isEqualTo(LifecycleStatus.ERROR)
    }

    private fun mockTile(name: LifecycleCoordinatorName): DominoTile {
        return mock {
            on { coordinatorName } doReturn name
            on { toNamedLifecycle() } doReturn NamedLifecycle(this.mock, mock())
        }
    }

    class SubscriptionDominoTile<K, V>(
        coordinatorFactory: LifecycleCoordinatorFactory,
        subscription: () -> Subscription<K, V>,
        subscriptionConfig: SubscriptionConfig,
        dependentChildren: Collection<LifecycleCoordinatorName>,
        managedChildren: Collection<NamedLifecycle>
    ): SubscriptionDominoTileBase(coordinatorFactory, subscription, subscriptionConfig, dependentChildren, managedChildren)

}
