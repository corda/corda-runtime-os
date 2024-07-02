package net.corda.applications.workers.verification

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.Health
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.Metrics
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.verification.VerificationProcessor
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine.Mixin

/** The worker for handling verification of ledger transactions. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class VerificationWorker @Activate constructor(
    @Reference(service = VerificationProcessor::class)
    private val verificationProcessor: VerificationProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleRegistry::class)
    private val lifecycleRegistry: LifecycleRegistry,
    @Reference(service = WebServer::class)
    private val webServer: WebServer,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
    @Reference(service = SecretsServiceFactoryResolver::class)
    val secretsServiceFactoryResolver: SecretsServiceFactoryResolver,
) : Application {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /** Parses the arguments, then initialises and starts the [verificationProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Verification worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("Verification Worker", platformInfoProvider)

        if (System.getProperty("co.paralleluniverse.fibers.verifyInstrumentation") == true.toString()) {
            logger.info("Quasar's instrumentation verification is enabled")
        }

        JavaSerialisationFilter.install()

        val params = getParams(args, VerificationWorkerParams())
        if (printHelpOrVersion(params.defaultParams, VerificationWorker::class.java, shutDownService)) return
        Metrics.configure(
            webServer,
            this.javaClass.simpleName,
            params.defaultParams.metricsKeepNames?.toRegex(),
            params.defaultParams.metricsDropLabels?.toRegex()
        )
        Health.configure(webServer, lifecycleRegistry)

        logger.info("Trace tags captured from the CLI: ${params.defaultParams.extraTraceTags}")
        configureTracing(
            "Verification Worker",
            params.defaultParams.zipkinTraceUrl,
            params.defaultParams.traceSamplesPerSecond,
            params.defaultParams.extraTraceTags
        )

        val config = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator())
        webServer.start(params.defaultParams.workerServerPort)
        verificationProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Verification worker stopping.")
        verificationProcessor.stop()
        webServer.stop()
        shutdownTracing()
    }
}

/** Additional parameters for the verification worker are added here. */
private class VerificationWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}