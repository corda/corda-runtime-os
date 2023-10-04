package net.corda.applications.workers.evm

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.Health
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.Metrics
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.tracing.configureTracing
import net.corda.tracing.shutdownTracing
import net.corda.web.api.WebServer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine.Mixin

/** The worker for interacting with the key material. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class EvmWorker @Activate constructor(
//    @Reference(service = EvmProcessor::class)
//    private val processor: EvmProcessor,
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

    override fun startup(args: Array<String>) {
        logger.info("Evm worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("Evm Worker", platformInfoProvider)

        JavaSerialisationFilter.install()
        val params = getParams(args, EvmWorkerParams())
        if (printHelpOrVersion(params.defaultParams, EvmWorker::class.java, shutDownService)) {
            return
        }
        Metrics.configure(webServer, this.javaClass.simpleName)
        Health.configure(webServer, lifecycleRegistry)

        configureTracing("Evm Worker", params.defaultParams.zipkinTraceUrl, params.defaultParams.traceSamplesPerSecond)
        webServer.start(params.defaultParams.workerServerPort)
//        processor.start(
//            buildBoostrapConfig(params, configurationValidatorFactory)
//        )
    }

    override fun shutdown() {
        logger.info("Evm worker stopping.")
//        processor.stop()
        shutdownTracing()
    }

    private fun buildBoostrapConfig(
        params: EvmWorkerParams,
        configurationValidatorFactory: ConfigurationValidatorFactory
    ): SmartConfig = getBootstrapConfig(
        secretsServiceFactoryResolver,
        params.defaultParams,
        configurationValidatorFactory.createConfigValidator(),
        listOf()
    )
}

class EvmWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()
}