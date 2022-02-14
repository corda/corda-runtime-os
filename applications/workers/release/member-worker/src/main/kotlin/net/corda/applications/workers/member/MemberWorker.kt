package net.corda.applications.workers.member

import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.HealthMonitor
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setUpHealthMonitor
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.member.MemberProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine.Mixin

/** The worker for handling member services. */
@Suppress("Unused")
@Component(service = [Application::class])
class MemberWorker @Activate constructor(
    @Reference(service = MemberProcessor::class)
    private val processor: MemberProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = HealthMonitor::class)
    private val healthMonitor: HealthMonitor
) : Application {

    private companion object {
        private val logger = contextLogger()
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("Member worker starting.")

        val params = getParams(args, MemberWorkerParams())
        if (printHelpOrVersion(params.defaultParams, MemberWorker::class.java, shutDownService)) return
        setUpHealthMonitor(healthMonitor, params.defaultParams)

        val config = getBootstrapConfig(params.defaultParams)

        processor.start(config)
    }

    override fun shutdown() {
        logger.info("Member worker stopping.")
        processor.stop()
        healthMonitor.stop()
    }
}

/** Additional parameters for the member worker are added here. */
private class MemberWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}