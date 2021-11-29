package net.corda.applications.workers.flow

import com.typesafe.config.Config
import net.corda.applications.workers.workercommon.Worker
import net.corda.osgi.api.Application
import net.corda.processors.flow.FlowProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

// TODO - Joel - Document.
@Component(service = [Application::class])
@Suppress("unused")
class FlowWorker: Worker() {
    private companion object {
        private val logger = contextLogger()
    }

    @Suppress("SpreadOperator")
    override fun startup(bootstrapConfig: Config) {
        logger.info("flow worker starting")
        bootstrapConfig.entrySet().forEach { entry ->
            logger.info(entry.key)
            logger.info(entry.value.toString())
            logger.info("")
        }

        FlowProcessor().startup(bootstrapConfig)

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