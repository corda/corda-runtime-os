package net.corda.applications.workers.db

import net.corda.applications.workers.workercommon.WorkerParams
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.osgi.api.Application
import net.corda.processors.db.DBProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The worker for interacting with the database. */
@Suppress("Unused")
@Component(service = [Application::class])
class DBWorker @Activate constructor(
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory,
    @Reference(service = DBProcessor::class)
    private val processor: DBProcessor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [DBProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("DB worker starting.")
        processor.config = WorkerParams().parseArgs(args, smartConfigFactory)
        processor.start()
    }

    override fun shutdown() {
        logger.info("DB worker stopping.")
        processor.stop()
    }
}