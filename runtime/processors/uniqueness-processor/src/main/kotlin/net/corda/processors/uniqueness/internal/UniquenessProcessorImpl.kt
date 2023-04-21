package net.corda.processors.uniqueness.internal

import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.processors.uniqueness.UniquenessProcessor
import net.corda.uniqueness.checker.UniquenessChecker
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/**
 * Uniqueness processor implementation.
 */
@Component(service = [UniquenessProcessor::class])
class UniquenessProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = UniquenessChecker::class)
    private val uniquenessChecker: UniquenessChecker
) : UniquenessProcessor {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val dependentComponents = DependentComponents.of(
        ::uniquenessChecker
    )

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<UniquenessProcessorImpl>(dependentComponents, ::eventHandler)

    override fun start() {
        log.info("Uniqueness processor starting.")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Uniqueness processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("Uniqueness processor received event $event.")
        when (event) {
            is StartEvent -> {
                // Nothing to do
            }
            is StopEvent -> {
                // Nothing to do
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Uniqueness processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            else -> {
                log.warn("Unexpected event $event!")
            }
        }
    }
}
