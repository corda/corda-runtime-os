package net.corda.crypto.component.impl

import net.corda.crypto.core.AbstractComponentNotReadyException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.utilities.trace
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractComponent<IMPL : AbstractComponent.AbstractImpl>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val myName: LifecycleCoordinatorName,
    private val upstream: DependenciesTracker
) : Lifecycle {
    interface AbstractImpl: AutoCloseable {
        override fun close() = Unit
    }

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val lifecycleCoordinator = coordinatorFactory.createCoordinator(myName, ::eventHandler)

    @Volatile
    private var _impl: IMPL? = null

    val impl: IMPL
        get() {
            val tmp = _impl
            if (tmp == null || lifecycleCoordinator.status != LifecycleStatus.UP) {
                throw AbstractComponentNotReadyException("Component $myName is not ready.")
            }
            return tmp
        }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.trace { "$myName starting..." }
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.trace  { "$myName stopping..." }
        lifecycleCoordinator.stop()
    }

    @Suppress("ComplexMethod", "NestedBlockDepth")
    protected open fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                upstream.follow(coordinator)
            }
            is StopEvent -> {
                onStop()
            }
            is RegistrationStatusChangeEvent -> {
                if (upstream.handle(event) == DependenciesTracker.EventHandling.HANDLED) {
                    if(_impl != null) {
                        val status = if(upstream.isUp) LifecycleStatus.UP else LifecycleStatus.DOWN
                        coordinator.updateStatus(status)
                    } else {
                        if (upstream.isUp) {
                            doActivate(coordinator)
                        } else {
                            coordinator.updateStatus(LifecycleStatus.DOWN)
                        }
                    }
                }
            }
        }
    }

    private fun onStop() {
        upstream.clear()
        _impl?.close()
        _impl = null
    }

    private fun doActivate(coordinator: LifecycleCoordinator) {
        logger.trace { "Creating active implementation" }
        _impl = createActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    protected abstract fun createActiveImpl(): IMPL
}