package net.corda.applications.testclients

import com.typesafe.config.ConfigFactory
import net.corda.components.examples.config.reader.ConfigReader
import net.corda.components.examples.config.reader.ConfigReader.Companion.MESSAGING_CONFIG
import net.corda.components.examples.config.reader.ConfigReceivedEvent
import net.corda.components.examples.config.reader.MessagingConfigUpdateEvent
import net.corda.components.examples.durable.RunChaosTestStringsDurableSub
import net.corda.components.examples.pubsub.RunChaosTestStringsPubSub
import net.corda.components.examples.stateevent.RunChaosTestStringsStateEventSub
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
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

enum class LifeCycleState {
    UNINITIALIZED, STARTINGCONFIG, STARTINGMESSAGING, REINITMESSAGING
}

@Component
class ChaosTestApp @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = ConfigReaderFactory::class)
    private var configReaderFactory: ConfigReaderFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    @Suppress("ComplexMethod", "SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting demo application...")
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        consoleLogger.info("Client type=${parameters.clientType}")
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
        } else {
            // All are LifeCycle objects
            var chaosTestStringsDurableSub: RunChaosTestStringsDurableSub? = null
            var chaosTestStringsPubsubSub: RunChaosTestStringsPubSub? = null
            var chaosTestStringsStateEventSub: RunChaosTestStringsStateEventSub? = null
            var configReader: ConfigReader? = null

            val instanceId = parameters.instanceId.toInt()

            val bootstrapConfig = getBootstrapConfig(instanceId)
            var state: LifeCycleState = LifeCycleState.UNINITIALIZED
            log.info("Creating life cycle coordinator")
            lifeCycleCoordinator =
                coordinatorFactory.createCoordinator<ChaosTestApp>() { event: LifecycleEvent, _: LifecycleCoordinator ->
                    log.info("LifecycleEvent received: $event")
                    when (event) {
                        is StartEvent -> {
                            consoleLogger.info("Starting kafka config reader")
                            state = LifeCycleState.STARTINGCONFIG
                            configReader?.start(bootstrapConfig)
                        }
                        is ConfigReceivedEvent -> {
                            if (state == LifeCycleState.STARTINGCONFIG) {
                                state = LifeCycleState.STARTINGMESSAGING
                                val config = bootstrapConfig.withFallback(event.currentConfigurationSnapshot[MESSAGING_CONFIG]!!)

                                if (parameters.clientType == "Durable") {
                                    chaosTestStringsDurableSub =
                                        RunChaosTestStringsDurableSub(
                                            subscriptionFactory,
                                            config,
                                            instanceId,
                                            parameters.durableKillProcessOnRecord.toInt(),
                                            parameters.durableProcessorDelay.toLong()
                                        )
                                    chaosTestStringsDurableSub?.start()
                                }

                                if (parameters.clientType == "StateEvent") {
                                    chaosTestStringsStateEventSub = RunChaosTestStringsStateEventSub(
                                        instanceId,
                                        config,
                                        subscriptionFactory,
                                        parameters.stateEventKillProcessOnRecord.toInt(),
                                        parameters.stateEventProcessorDelay.toLong()
                                    )
                                    chaosTestStringsStateEventSub?.start()
                                }

                                if (parameters.clientType == "Sub") {
                                    println("Running RunChaosTestStringsPubSub")
                                    chaosTestStringsPubsubSub = RunChaosTestStringsPubSub(subscriptionFactory, config)
                                    chaosTestStringsPubsubSub?.start()
                                }
                                consoleLogger.info("Received config from kafka, started subscriptions")
                            }
                        }
                        is MessagingConfigUpdateEvent -> {
                            state = LifeCycleState.REINITMESSAGING
                            val config = bootstrapConfig.withFallback(event.currentConfigurationSnapshot[MESSAGING_CONFIG]!!)
                            chaosTestStringsStateEventSub?.reStart(config)
                            chaosTestStringsPubsubSub?.reStart(config)
                            chaosTestStringsDurableSub?.reStart(config)
                            consoleLogger.info("Received config update from kafka, restarted subscriptions")
                        }
                        is StopEvent -> {
                            configReader?.stop()
                            chaosTestStringsDurableSub?.stop()
                            chaosTestStringsStateEventSub?.stop()
                            chaosTestStringsPubsubSub?.stop()
                            shutdown()
                        }
                        else -> {
                            log.error("$event unexpected!")
                        }
                    }
                }
            configReader = ConfigReader(lifeCycleCoordinator!!, configReaderFactory)

            log.info("Starting life cycle coordinator")
            lifeCycleCoordinator!!.start()
            consoleLogger.info("Demo application started")
        }
    }

    private fun getBootstrapConfig(instanceId: Int?): SmartConfig {
        // NOTE: this doesn't make sense as it's recursive.
        //return smartConfigFactory.create(getBootstrapConfig(instanceId))
        println(instanceId)
        val config = ConfigFactory.empty()
        return SmartConfigFactory.create(config).create(config)
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

    @CommandLine.Option(
        names = ["--clientType"],
        description = ["Specify list of clients we want to run, currently the choice is one {Durable, StateEvent, Sub}"]
    )
    var clientType = "0"

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}
