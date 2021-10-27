package net.corda.applications.session.manager

import com.typesafe.config.Config
import net.corda.applications.common.ConfigHelper.Companion.getBootstrapConfig
import net.corda.components.session.manager.SessionManager
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.osgi.api.Application
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Component
class SessionManagerApp @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager,
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null
    private var bootstrapConfig: Config? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting Session Manager application...")
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        bootstrapConfig = getBootstrapConfig(parameters.instanceId.toInt())

        log.info("Starting life cycle coordinator for Session Manager")
        lifeCycleCoordinator = coordinatorFactory.createCoordinator<SessionManagerApp> { event: LifecycleEvent, _:  LifecycleCoordinator ->
            log.info("Session Manager received: $event")
            consoleLogger.info("Session Manager received: $event")

            when (event) {
                is StartEvent -> {
                    configurationReadService.start()
                    configurationReadService.bootstrapConfig(bootstrapConfig!!)
                    sessionManager.start()
                }
                is StopEvent -> {
                    configurationReadService.stop()
                    sessionManager.stop()
                }
                else -> {
                    log.error("$event unexpected!")
                }
            }
        }
        lifeCycleCoordinator?.start()
        consoleLogger.info("Session Manager application started")
    }

    override fun shutdown() {
        consoleLogger.info("Stopping application")
        lifeCycleCoordinator?.stop()
        log.info("Stopping application")
    }
}

class CliParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker"])
    lateinit var instanceId: String
}
