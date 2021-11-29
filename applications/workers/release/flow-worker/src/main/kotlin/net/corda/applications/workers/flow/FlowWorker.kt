package net.corda.applications.workers.flow

import com.typesafe.config.Config
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

    /** Starts the [FlowProcessor], passing in the [busConfig]. */
    @Suppress("SpreadOperator")
    override fun startup(busConfig: Config) {
        logger.info("Flow worker starting")
        FlowProcessor().startup(busConfig)

        // Sets the worker to unhealthy after 30 seconds.
//        var x = 0
//        while (true) {
//            Thread.sleep(1000)
//            println(x++)
//            if (x > 30) {
//                healthProvider.setIsUnhealthy()
//            }
//        }
    }
}