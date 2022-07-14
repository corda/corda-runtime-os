package net.corda.applications.workers.test

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setUpHealthMonitor
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.test.TestProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin

/** The worker for handling flows. */
@Suppress("Unused")
@Component(service = [Application::class])
class TestWorker @Activate constructor(
    @Reference(service = TestProcessor::class)
    private val testProcessor: TestProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [testProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Test worker starting.")

        val params = getParams(args, TestWorkerParams())
        if (printHelpOrVersion(params.defaultParams, TestWorker::class.java, shutDownService)) return
        setUpHealthMonitor(healthMonitor, params.defaultParams)

        val config = getBootstrapConfig(params.defaultParams, configurationValidatorFactory.createConfigValidator())

        testProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Test worker stopping.")
        testProcessor.stop()
        healthMonitor.stop()
    }
}

/** Additional parameters for the test worker are added here. */
private class TestWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}