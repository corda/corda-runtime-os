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
import net.corda.utilities.debug
import net.corda.utilities.trace
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
                        _impl?.onRegistrationStatusChange(upstream.isUp)
                    } else {
                        if (upstream.isUp) {
                            doActivate(coordinator)
                        } else {
                            coordinator.updateStatus(LifecycleStatus.DOWN)
                        }
                    }
                }
            }
            is AbstractConfigurableComponent.TryAgainCreateActiveImpl -> {
                if(_impl == null) {
                    doActivate(coordinator)
                } else {
                    if(upstream.isUp) {
                        logger.trace { "TryAgainCreateActiveImpl - $myName - setting as UP." }
                        coordinator.updateStatus(LifecycleStatus.UP)
                    } else {
                        logger.trace { "TryAgainCreateActiveImpl - $myName - skipping as stale as _impl already created." }
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
        try {
            _impl = createActiveImpl()
            activationFailureCounter.set(0)
        } catch (e: FatalActivationException) {
            logger.error("$myName failed activate", e)
            coordinator.updateStatus(LifecycleStatus.ERROR)
            return
        } catch (e: Throwable) {
            if(activationFailureCounter.incrementAndGet() <= 5) {
                logger.debug { "$myName failed activate..., will try again. Cause: ${e.message}" }
                coordinator.postEvent(TryAgainCreateActiveImpl())
            } else {
                logger.error("$myName failed activate, giving up", e)
                coordinator.updateStatus(LifecycleStatus.ERROR)
            }
            return
        }
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    protected abstract fun createActiveImpl(): IMPL

    class TryAgainCreateActiveImpl : LifecycleEvent
}