package net.corda.applications.workers.flow

import com.typesafe.config.Config
import net.corda.applications.workers.workercommon.HealthProvider
import net.corda.applications.workers.workercommon.Worker
import net.corda.osgi.api.Application
import net.corda.processors.flow.FlowProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

/** The [Worker] for handling flows. */
@Suppress("unused")
@Component(service = [Application::class])
class FlowWorker: Worker() {
    private companion object {
        private val logger = contextLogger()
    }

    /** Starts the [FlowProcessor], passing in the [healthProvider] and [busConfig]. */
    @Suppress("SpreadOperator")
    override fun startup(healthProvider: HealthProvider, busConfig: Config) {
        logger.info("Flow worker starting")
        FlowProcessor().startup(healthProvider, busConfig)
    }
}