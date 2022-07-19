package net.corda.crypto.component.impl

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractComponent<IMPL : AbstractComponent.AbstractImpl>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val myName: LifecycleCoordinatorName,
    private val upstream: DependenciesTracker
) : Lifecycle {
    interface AbstractImpl: AutoCloseable {
        override fun close() = Unit
        fun onRegistrationStatusChange(upstreamIsUp: Boolean) = Unit
    }

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val lifecycleCoordinator = coordinatorFactory.createCoordinator(myName, ::eventHandler)

    @Volatile
    private var _impl: IMPL? = null

    val impl: IMPL
        get() {
            val tmp = _impl
            if (tmp == null || lifecycleCoordinator.status != LifecycleStatus.UP) {
                throw IllegalStateException("Component $myName is not ready.")
            }
            return tmp
        }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.info("Stopping...")
        lifecycleCoordinator.stop()
    }

    protected open fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("LifecycleEvent received: $event")
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
                        coordinator.updateStatus(if(upstream.isUp) LifecycleStatus.UP else LifecycleStatus.DOWN)
                        _impl?.onRegistrationStatusChange(upstream.isUp)
                    } else {
                        if (upstream.isUp) {
                            doActivate(coordinator)
                        } else {
                            coordinator.updateStatus(LifecycleStatus.DOWN)
                        }
                    }
                    doActivate(coordinator)
                }
            }
            is TryAgainCreateActiveImpl -> {
                doActivate(coordinator)
            }
        }
    }

    private fun onStop() {
        upstream.clear()
        _impl?.close()
        _impl = null
    }

    private fun doActivate(coordinator: LifecycleCoordinator) {
        if (_impl == null) {
            logger.info("Creating active implementation")
            try {
                _impl = createActiveImpl()
            } catch (e: Throwable) {
                logger.error("Failed to create active implementation, will try again...")
                coordinator.postEvent(TryAgainCreateActiveImpl())
                return
            }
        }
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    protected abstract fun createActiveImpl(): IMPL

    class TryAgainCreateActiveImpl : LifecycleEvent
}