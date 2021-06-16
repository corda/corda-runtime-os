package net.corda.applications.examples.publisher

import net.corda.components.examples.publisher.RunPublisher
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.LifeCycleEvent
import net.corda.lifecycle.SimpleLifeCycleCoordinator
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import picocli.CommandLine

@Component
class DemoPublisher @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        const val BATCH_SIZE: Int = 128
        const val TIMEOUT: Long = 10000L
    }

    private var lifeCycleCoordinator: LifeCycleCoordinator? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            val instanceId = parameters.instanceId.toInt()
            var publisher: RunPublisher? = null

            lifeCycleCoordinator = SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
                log.debug("processEvent $event")
                when (event) {
                    is StartEvent -> {
                        publisher!!.start()
                    }
                    is StopEvent -> {
                        publisher?.stop()
                        shutdownOSGiFramework()
                    }
                    else -> {
                        log.error("$event unexpected!")
                    }
                }
            }

            publisher = RunPublisher(lifeCycleCoordinator!!, publisherFactory, instanceId, parameters.numberOfRecords.toInt())

            lifeCycleCoordinator!!.start()
        }
    }

    override fun shutdown() {
        log.info("Stopping application")
        lifeCycleCoordinator?.stop()
    }

    private fun shutdownOSGiFramework() {
        val bundleContext: BundleContext? = FrameworkUtil.getBundle(this::class.java).bundleContext
        if (bundleContext != null) {
            val shutdownServiceReference: ServiceReference<Shutdown>? =
                bundleContext.getServiceReference(Shutdown::class.java)
            if (shutdownServiceReference != null) {
                bundleContext.getService(shutdownServiceReference)?.shutdown(bundleContext.bundle)
            }
        }
    }
}

class CliParameters {
    @CommandLine.Option(names = ["--instanceId"], description = ["InstanceId for this worker"])
    lateinit var instanceId: String

    @CommandLine.Option(names = ["--numberOfRecords"], description = ["Batch size of records to send 4 times. " +
            "2 as async, 2 as transactional"])
    lateinit var numberOfRecords: String

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}
