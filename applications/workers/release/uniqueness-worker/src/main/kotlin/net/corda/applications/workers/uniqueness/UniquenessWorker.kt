package net.corda.applications.workers.uniqueness

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setupMonitor
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.uniqueness.UniquenessProcessor
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine.Mixin

/** The worker for uniqueness checks. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class UniquenessWorker @Activate constructor(
    @Reference(service = UniquenessProcessor::class)
    private val uniquenessProcessor: UniquenessProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = WorkerMonitor::class)
    private val workerMonitor: WorkerMonitor,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
) : Application {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /** Parses the arguments, then initialises and starts the [processor]. */
    override fun startup(args: Array<String>) {
        logger.info("Uniqueness worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("Uniqueness Worker", platformInfoProvider)

        JavaSerialisationFilter.install()

        val params = getParams(args, UniquenessWorkerParams())
        if (printHelpOrVersion(params.defaultParams, UniquenessWorker::class.java, shutDownService)) return
        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        configureTracing("Uniqueness Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)

        uniquenessProcessor.start()
    }

    override fun shutdown() {
        logger.info("Uniqueness worker stopping.")
        workerMonitor.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the uniqueness worker are added here. */
private class UniquenessWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}