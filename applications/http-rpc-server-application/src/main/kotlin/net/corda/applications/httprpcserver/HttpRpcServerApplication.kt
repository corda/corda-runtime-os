package net.corda.applications.httprpcserver

import net.corda.lifecycle.*
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Component(immediate = true)
class HttpRpcServerApplication @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleCoordinatorFactory::class)
private val coordinatorFactory: LifecycleCoordinatorFactory
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")

    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting HttpRpcServerApplication...")

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {



            lifeCycleCoordinator = coordinatorFactory.createCoordinator<HttpRpcServerApplication>(
            ) { event: LifecycleEvent, _: LifecycleCoordinator ->
                log.info("LifecycleEvent received: $event")
                when (event) {
                    is StartEvent -> {
                        //httprpcservercomponent start()
                    }
                    is StopEvent -> {
                        //httprpcservercomponent?.stop()
                        shutdownOSGiFramework()
                    }
                    else -> {
                        log.error("$event unexpected!")
                    }
                }
            }

           //create httprpcservercomponent

            lifeCycleCoordinator!!.start()
            consoleLogger.info("Finished HttpRpcServerApplication startup")
        }
    }

    override fun shutdown() {
        consoleLogger.info("Stopping HttpRpcServerApplication")
        log.info("Stopping application")
        lifeCycleCoordinator?.stop()
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

}

class CliParameters {

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}

