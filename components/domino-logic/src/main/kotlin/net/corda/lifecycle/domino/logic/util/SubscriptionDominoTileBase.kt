package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.lifecycle.domino.logic.DominoTileState.Created
import net.corda.lifecycle.domino.logic.DominoTileState.Started
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedByParent
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToBadConfig
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToChildStopped
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToError
import net.corda.lifecycle.domino.logic.StatusChangeEvent
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A class encapsulating the domino logic for subscriptions.
 * When [start] is invoked, it will first start all [managedChildren].
 * It will then wait until all [dependentChildren] have fully started and afterwards it will start the subscription.
 * In the event that any of the [dependentChildren] goes down, it will stop the subscription. It will start it again if they all recover.
 * If the subscription goes down ([LifecycleStatus.DOWN] or [LifecycleStatus.ERROR]), it will propagate the error upstream.
 *
 * @param subscription the subscription that will be controlled (started/stopped) by this class.
 * @param subscriptionName the coordinator name of the provided subscription.
 * @param dependentChildren the children the subscription will depend on for processing messages
 *   (it will be processing messages only if they are all up).
 * @param managedChildren the children that the class will start, when it is started.
 */
abstract class SubscriptionDominoTileBase(
    coordinatorFactory: LifecycleCoordinatorFactory,
    // Lifecycle type is used, because there is no single type capturing all subscriptions. Type checks are executed at runtime.
    private val subscription: Lifecycle,
    private val subscriptionName: LifecycleCoordinatorName,
    final override val dependentChildren: Collection<DominoTile>,
    final override val managedChildren: Collection<DominoTile>
): DominoTile {

    companion object {
        private val instancesIndex = ConcurrentHashMap<String, Int>()
    }

    init {
        require(
            subscription is Subscription<*,*> ||
            subscription is RPCSubscription<*,*> ||
            subscription is StateAndEventSubscription<*,*,*> ||
            subscription is CompactedSubscription<*,*>
        ) { "Expected subscription type, but got ${subscription.javaClass.simpleName}" }
    }

    final override val coordinatorName: LifecycleCoordinatorName by lazy {
        LifecycleCoordinatorName(
            "$subscriptionName-tile",
            instancesIndex.compute(this::class.java.simpleName) { _, last ->
                if (last == null) {
                    1
                } else {
                    last + 1
                }
            }.toString()
        )
    }

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, EventHandler())

    private val currentState = AtomicReference(Created)

    private val isOpen = AtomicBoolean(true)

    override val state: DominoTileState
        get() = currentState.get()
    override val isRunning: Boolean
        get() = state == Started

    private val dependentChildrenRegistration = coordinator.followStatusChangesByName(dependentChildren.map { it.coordinatorName }.toSet())
    private val subscriptionRegistration = coordinator.followStatusChangesByName(setOf(subscriptionName))

    private val logger = LoggerFactory.getLogger(coordinatorName.toString())

    override fun start() {
        coordinator.start()
        managedChildren.forEach { it.start() }
        if (dependentChildren.isEmpty()) {
            subscription.start()
        }
    }

    override fun stop() {
        managedChildren.forEach { it.stop() }
    }

    override fun close() {
        stop()
        isOpen.set(false)
    }

    private fun updateState(newState: DominoTileState) {
        val oldState = currentState.getAndSet(newState)
        if (newState != oldState) {
            val status = when (newState) {
                Started -> LifecycleStatus.UP
                StoppedDueToBadConfig, StoppedByParent, StoppedDueToChildStopped -> LifecycleStatus.DOWN
                StoppedDueToError -> LifecycleStatus.ERROR
                Created -> null
            }
            status?.let { coordinator.updateStatus(it) }
            coordinator.postCustomEventToFollowers(StatusChangeEvent(newState))
            logger.info("State updated from $oldState to $newState")
        }
    }

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            if (!isOpen.get()) {
                return
            }

            handleEvent(event)
        }

        private fun handleEvent(event: LifecycleEvent) {
            when(event) {
                is RegistrationStatusChangeEvent -> {
                    when(event.registration) {
                        subscriptionRegistration -> {
                            when(event.status) {
                                LifecycleStatus.UP -> {
                                    updateState(Started)
                                }
                                LifecycleStatus.DOWN -> {
                                    updateState(StoppedDueToChildStopped)
                                }
                                LifecycleStatus.ERROR -> {
                                    updateState(StoppedDueToError)
                                }
                            }
                        }
                        dependentChildrenRegistration -> {
                            when(event.status) {
                                LifecycleStatus.UP -> {
                                    println("QQQ for $coordinatorName - All is up, going up $dependentChildren")
                                    logger.info("All dependencies are started now, starting subscription.")
                                    subscription.start()
                                }
                                LifecycleStatus.DOWN -> {
                                    println("QQQ for $coordinatorName - Something is down, going down, $dependentChildren")
                                    logger.info("One of the dependencies went down, stopping subscription.")
                                    subscription.stop()
                                }
                                LifecycleStatus.ERROR -> {
                                    println("QQQ for $coordinatorName - had error, going down, $dependentChildren")
                                    logger.info("One of the dependencies had an error, stopping subscription.")
                                    subscription.stop()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}