package net.corda.processors.uniqueness.internal

import net.corda.lifecycle.*
import net.corda.processors.uniqueness.UniquenessProcessor
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Uniqueness processor implementation. Currently a bare-bones implementation that will instantiate
 * an in-memory uniqueness checker component.
 */
@Component(service = [UniquenessProcessor::class])
class UniquenessProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = UniquenessChecker::class)
    private val uniquenessChecker: UniquenessChecker
) : UniquenessProcessor {

    companion object {
        private val log = contextLogger()
    }

    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<UniquenessProcessorImpl>(::eventHandler)

    private val dependentComponents = DependentComponents.of(
        ::uniquenessChecker
    )

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
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
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
