package net.corda.cpk.read.impl.testing

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Does nothing other than wait for other components and notifies on the given object when "up".  This is
 * meant to be used in integration tests.
 *
 *      val waiter = WaitingComponent(coordinatorFactory)
 *
 *      val latch = CountDownLatch(1)  // use latch, since we can specify a timeout, rather than synchronized(lock)
 *      waiter.onUp { latch.countDown() }
 *      waiter.onWait { latch.await(3, TimeUnit.SECONDS) }
 *      waiter.waitFor(LifecycleCoordinatorName.forComponent<OtherComponent>())
 *      waiter.start()
 *      otherComponent.start()
 *      ...
 *      waiter.waitForLifecycleStatusUp()
 *
 * Do not `@Inject` - just `@Inject` the LifecycleCoordinatorFactory, and then create a regular, plain-old Kotlin
 * version of this class, i.e.
 */
class WaitingComponent(coordinatorFactory: LifecycleCoordinatorFactory) : Lifecycle, LifecycleEventHandler {
    private lateinit var onWaitFunction: () -> Unit
    private lateinit var onUpCallback: () -> Unit
    private val coordinator = coordinatorFactory.createCoordinator<WaitingComponent>(this)
    private var registration: RegistrationHandle? = null

    private val names = mutableSetOf<LifecycleCoordinatorName>()

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() = coordinator.start()
    override fun stop() = coordinator.stop()

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStart()
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
            is StopEvent -> onStop()
        }
    }

    private fun onStart() {
        if (names.isEmpty()) {
            throw CordaRuntimeException("WaitingComponent is not waiting on anything - no list of coordinators has been given")
        }
        registration?.close()
        registration = coordinator.followStatusChangesByName(names)
    }

    private fun onStop() {
        registration?.close()
        registration = null
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        if (event.status == LifecycleStatus.UP) {
            onUpCallback()
        }
    }

    /** Add a(nother) [LifecycleCoordinator] to wait on for its [LifecycleStatus.UP] status */
    fun waitFor(name: LifecycleCoordinatorName) = names.add(name)

    /** Wait for [LifecycleStatus.UP] - the behaviour of this function is dependent on what was set via [onWait] */
    fun waitForLifecycleStatusUp() {
        onWaitFunction()
    }

    /** Sets the function we call when [waitForLifecycleStatusUp] is called */
    fun onWait(function: () -> Unit) {
        onWaitFunction = function
    }

    /** Sets the function we call when a [LifecycleStatus.UP] event occurs */
    fun onUp(function: () -> Unit) {
        onUpCallback = function
    }
}
