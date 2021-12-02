package net.corda.applications.workers.flow

import net.corda.applications.workers.healthprovider.HealthProvider
import net.corda.applications.workers.workercommon.Worker
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.osgi.api.Application
import net.corda.processors.flow.FlowProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The [Worker] for handling flows. */
@Suppress("Unused")
@Component(service = [Application::class])
class FlowWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    smartConfigFactory: SmartConfigFactory,
    @Reference(service = HealthProvider::class)
    healthProvider: HealthProvider,
    @Reference(service = FlowProcessor::class)
    private val flowProcessor: FlowProcessor,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : Worker(smartConfigFactory, healthProvider) {

    private companion object {
        private val logger = contextLogger()
    }

    // Passes start and stop events through to the flow processor.
    private val eventHandler = LifecycleEventHandler { event, _ ->
        when (event) {
            is StartEvent -> flowProcessor.start()
            is StopEvent -> flowProcessor.stop()
        }
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowProcessor>(eventHandler)

    // TODO - Joel - Compare this to VirtualNodeInfoServiceComponentImpl for correctness.

    // TODO - Joel - Create all-in-one processor - a worker that bootstraps multiple processors.

    /** Starts the [FlowProcessor]. */
    override fun startup(config: SmartConfig) {
        logger.info("Flow worker starting.")
        initialiseFlowProcessor(config)
        coordinator.start()
    }

    // TODO - Joel - Create method to stop processor and coordinator.

    /** Initialises the [flowProcessor]. */
    private fun initialiseFlowProcessor(config: SmartConfig) {
        flowProcessor.config = config
        flowProcessor.onStatusUpCallback = ::setStatusToUp
        flowProcessor.onStatusDownCallback = ::setStatusToDown
        flowProcessor.onStatusErrorCallback = ::setStatusToError
    }

    /** Sets the coordinator's status to [LifecycleStatus.UP]. */
    private fun setStatusToUp() = coordinator.updateStatus(LifecycleStatus.UP)

    /** Sets the coordinator's status to [LifecycleStatus.DOWN]. */
    private fun setStatusToDown() = coordinator.updateStatus(LifecycleStatus.DOWN)

    /** Sets the coordinator's status to [LifecycleStatus.ERROR]. */
    private fun setStatusToError() = coordinator.updateStatus(LifecycleStatus.ERROR)
}