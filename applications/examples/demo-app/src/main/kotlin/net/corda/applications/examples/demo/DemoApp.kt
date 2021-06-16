package net.corda.applications.examples.demo

import net.corda.comp.kafka.config.write.KafkaConfigWrite
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.components.examples.bootstrap.topics.BootstrapConfigTopic
import net.corda.components.examples.bootstrap.topics.TopicsCreatedEvent
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
import java.io.File

enum class LifeCycleState {
    UNINITIALIZED, STARTINGCONFIG, STARTINGMESSAGING, REINITMESSAGING
}

@Component
class DemoApp @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = KafkaTopicAdmin::class)
    private var topicAdmin: KafkaTopicAdmin,
    @Reference(service = KafkaConfigWrite::class)
    private var configWriter: KafkaConfigWrite,
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
            var bootstrapConfigTopic: BootstrapConfigTopic? = null
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
                            bootstrapConfigTopic?.start()
                        }
                        is TopicsCreatedEvent -> {
                            if (state == LifeCycleState.UNINITIALIZED) {
                                state = LifeCycleState.STARTINGCONFIG
                                configReader?.start()
                            }
                        }
                        is ConfigReceivedEvent -> {
                            val config = configReader!!.getConfiguration(KAFKA_CONFIG)

                            state = LifeCycleState.STARTINGMESSAGING
                            compactedSub = RunCompactedSub(lifeCycleCoordinator, subscriptionFactory, config)
                            durableSub =
                                RunDurableSub(
                                    lifeCycleCoordinator,
                                    subscriptionFactory,
                                    config,
                                    instanceId,
                                    parameters.durableKillProcessOnRecord.toInt()
                                )
                            stateEventSub = RunStateEventSub(
                                lifeCycleCoordinator,
                                instanceId,
                                config,
                                subscriptionFactory,
                                parameters.stateEventKillProcessOnRecord.toInt()
                            )
                            pubsubSub = RunPubSub(lifeCycleCoordinator, subscriptionFactory, config)

                            compactedSub?.start()
                            durableSub?.start()
                            stateEventSub?.start()
                            pubsubSub?.start()
                        }
                        is KafkaConfigUpdateEvent -> {
                            state = LifeCycleState.REINITMESSAGING
                            val config = configReader!!.getConfiguration(KAFKA_CONFIG)
                            compactedSub?.reStart(config)
                            durableSub?.reStart(config)
                            stateEventSub?.reStart(config)
                            pubsubSub?.reStart(config)
                        }
                        is StopEvent -> {
                            configReader?.stop()
                            bootstrapConfigTopic?.stop()
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

            bootstrapConfigTopic = BootstrapConfigTopic(
                lifeCycleCoordinator!!,
                topicAdmin,
                configWriter,
                parameters.kafkaConnection,
                parameters.topicTemplate,
                parameters.configurationFile
            )

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

    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties"])
    lateinit var kafkaConnection: File

    @CommandLine.Option(names = ["--topic"], description = ["File containing the topic template"])
    lateinit var topicTemplate: File

    @CommandLine.Option(names = ["--config"], description = ["File containing configuration to be stored"])
    lateinit var configurationFile: File

    @CommandLine.Option(
        names = ["--durableKillProcessOnRecord"],
        description = ["Exit process via durable processor on this record count"]
    )
    var durableKillProcessOnRecord: String = "0"

    @CommandLine.Option(
        names = ["--stateEventKillProcessOnRecord"],
        description = ["Exit process via state plus event processor on this record count"]
    )
    var stateEventKillProcessOnRecord: String = "0"

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}
