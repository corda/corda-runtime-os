package net.corda.applications.examples.demo

import net.corda.components.examples.compacted.RunCompactedSub
import net.corda.components.examples.config.reader.ConfigReader
import net.corda.components.examples.config.reader.ConfigReader.Companion.KAFKA_CONFIG
import net.corda.components.examples.config.reader.ConfigReceivedEvent
import net.corda.components.examples.config.reader.KafkaConfigUpdateEvent
import net.corda.components.examples.durable.RunDurableSub
import net.corda.components.examples.pubsub.RunPubSub
import net.corda.components.examples.stateevent.RunStateEventSub
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.LifeCycleEvent
import net.corda.lifecycle.SimpleLifeCycleCoordinator
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
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

enum class LifeCycleState {
    UNINITIALIZED, STARTINGCONFIG, STARTINGMESSAGING, REINITMESSAGING
}

@Component
class DemoApp @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigReadServiceFactory::class)
    private var configReadServiceFactory: ConfigReadServiceFactory
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
            var compactedSub: RunCompactedSub? = null
            var durableSub: RunDurableSub? = null
            var pubsubSub: RunPubSub? = null
            var stateEventSub: RunStateEventSub? = null
            var configReader: ConfigReader? = null

            val instanceId = parameters.instanceId.toInt()
            var state: LifeCycleState = LifeCycleState.UNINITIALIZED
            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator =
                SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, lifeCycleCoordinator: LifeCycleCoordinator ->
                    log.info("LifecycleEvent received: $event")
                    when (event) {
                        is StartEvent -> {
                            state = LifeCycleState.STARTINGCONFIG
                            configReader?.start()
                        }
                        is ConfigReceivedEvent -> {
                            if (state == LifeCycleState.STARTINGCONFIG) {
                                state = LifeCycleState.STARTINGMESSAGING
                                val config = event.currentConfigurationSnapshot[KAFKA_CONFIG]!!

                                compactedSub = RunCompactedSub(subscriptionFactory, config)
                                durableSub =
                                    RunDurableSub(
                                        lifeCycleCoordinator,
                                        subscriptionFactory,
                                        config,
                                        instanceId,
                                        parameters.durableKillProcessOnRecord.toInt(),
                                        parameters.durableProcessorDelay.toLong()
                                    )
                                stateEventSub = RunStateEventSub(
                                    instanceId,
                                    config,
                                    subscriptionFactory,
                                    parameters.stateEventKillProcessOnRecord.toInt(),
                                    parameters.stateEventProcessorDelay.toLong()
                                )
                                pubsubSub = RunPubSub(subscriptionFactory, config)

                                compactedSub?.start()
                                durableSub?.start()
                                stateEventSub?.start()
                                pubsubSub?.start()
                            }
                        }
                        is KafkaConfigUpdateEvent -> {
                            state = LifeCycleState.REINITMESSAGING
                            val config = event.currentConfigurationSnapshot[KAFKA_CONFIG]!!
                            compactedSub?.reStart(config)
                            durableSub?.reStart(config)
                            stateEventSub?.reStart(config)
                            pubsubSub?.reStart(config)
                        }
                        is StopEvent -> {
                            configReader?.stop()
                            compactedSub?.stop()
                            durableSub?.stop()
                            stateEventSub?.stop()
                            pubsubSub?.stop()
                        }
                        else -> {
                            log.error("$event unexpected!")
                        }
                    }
                }
            configReader = ConfigReader(lifeCycleCoordinator!!, configReadServiceFactory)

            log.info("Starting life cycle coordinator")
            lifeCycleCoordinator!!.start()
        }
    }

    override fun shutdown() {
        lifeCycleCoordinator?.stop()
        log.info("Stopping application")
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

    @CommandLine.Option(
        names = ["--durableKillProcessOnRecord"],
        description = ["Exit process via durable processor on this record count"]
    )
    var durableKillProcessOnRecord: String = "0"

    @CommandLine.Option(
        names = ["--durableProcessorDelay"],
        description = ["Milli seconds delay between processing each record returned from kafka in the durable processor"]
    )
    var durableProcessorDelay: String = "0"

    @CommandLine.Option(
        names = ["--stateEventProcessorDelay"],
        description = ["Milli seconds delay between processing each record returned from kafka in the state and event processor"]
    )
    var stateEventProcessorDelay: String = "0"

    @CommandLine.Option(
        names = ["--stateEventKillProcessOnRecord"],
        description = ["Exit process via state and event processor on this record count"]
    )
    var stateEventKillProcessOnRecord: String = "0"

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}
