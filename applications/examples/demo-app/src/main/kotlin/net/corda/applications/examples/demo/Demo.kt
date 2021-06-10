package net.corda.applications.examples.demo

import net.corda.comp.kafka.config.write.KafkaConfigWrite
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.components.examples.bootstrap.topics.BootstrapConfigTopic
import net.corda.components.examples.bootstrap.topics.ConfigCompleteEvent
import net.corda.components.examples.compacted.RunCompactedSub
import net.corda.components.examples.durable.RunDurableSub
import net.corda.components.examples.publisher.RunPublisher
import net.corda.components.examples.pubsub.RunPubSub
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.LifeCycleEvent
import net.corda.lifecycle.SimpleLifeCycleCoordinator
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File

@Component
class Demo @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = KafkaTopicAdmin::class)
    private var topicAdmin: KafkaTopicAdmin,
    @Reference(service = KafkaConfigWrite::class)
    private var configWriter: KafkaConfigWrite
) : Application {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        const val BATCH_SIZE: Int = 128
        const val TIMEOUT: Long = 10000L
    }

    private var lifeCycleCoordinator: LifeCycleCoordinator? = null

    override fun startup(args: Array<String>) {
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            var bootstrapConfigTopic: BootstrapConfigTopic? = null

            val instanceId = parameters.instanceId.toInt()
            val compactedSub = RunCompactedSub(subscriptionFactory)
            val durableSub = RunDurableSub(subscriptionFactory, instanceId)
            val pubsubSub = RunPubSub(subscriptionFactory)
            val publisher = RunPublisher(publisherFactory, instanceId)

            lifeCycleCoordinator = SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
                log.debug("processEvent $event")
                when (event) {

                    is StartEvent -> {
                        bootstrapConfigTopic?.start()
                    }

                    is ConfigCompleteEvent -> {
                        pubsubSub.start()
                        compactedSub.start()
                        durableSub.start()
                        publisher.start()
                    }

                    is StopEvent -> {
                        bootstrapConfigTopic?.stop()
                        pubsubSub.stop()
                        compactedSub.stop()
                        durableSub.stop()
                        publisher.stop()
                    }
                    else -> {
                        log.error("$event unexpected!")
                    }
                }
            }

            bootstrapConfigTopic = BootstrapConfigTopic(
                lifeCycleCoordinator!!,
                topicAdmin,
                configWriter,
                parameters.kafkaConnection,
                parameters.topicTemplate,
                parameters.configurationFile
            )

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

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}
