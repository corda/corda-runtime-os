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
import java.util.concurrent.atomic.AtomicInteger

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

    private val activationFailureCounter = AtomicInteger(0)

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
                        val status = if(upstream.isUp) LifecycleStatus.UP else LifecycleStatus.DOWN
                        logger.info("RegistrationStatusChangeEvent - setting as {}.", status)
                        coordinator.updateStatus(status)
                        _impl?.onRegistrationStatusChange(upstream.isUp)
                    } else {
                        if (upstream.isUp) {
                            doActivate(coordinator)
                        } else {
                            logger.info("RegistrationStatusChangeEvent - setting as DOWN.")
                            coordinator.updateStatus(LifecycleStatus.DOWN)
                        }
                    }
                }
            }
            is TryAgainCreateActiveImpl -> {
                if(_impl == null) {
                    doActivate(coordinator)
                } else {
                    if(upstream.isUp) {
                        logger.info("TryAgainCreateActiveImpl - setting as UP.")
                        coordinator.updateStatus(LifecycleStatus.UP)
                    } else {
                        logger.info("TryAgainCreateActiveImpl - skipping as stale as _impl already created.")
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
        logger.info("Creating active implementation")
        try {
            _impl = createActiveImpl()
            activationFailureCounter.set(0)
        } catch (e: Throwable) {
            if(activationFailureCounter.incrementAndGet() <= 5) {
                logger.warn("Failed activate..., will try again", e)
                coordinator.postEvent(TryAgainCreateActiveImpl())
            } else {
                logger.error("Failed activate, giving up", e)
                coordinator.updateStatus(LifecycleStatus.ERROR)
            }
            return
        }
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    protected abstract fun createActiveImpl(): IMPL

    class TryAgainCreateActiveImpl : LifecycleEvent
}