package net.corda.applications.examples.demo

import net.corda.components.examples.bootstrap.topics.BootstrapTopics
import net.corda.components.examples.bootstrap.topics.ConfigCompleteEvent
import net.corda.components.examples.compacted.RunCompactedSub
import net.corda.components.examples.durable.RunDurableSub
import net.corda.components.examples.publisher.RunPublisher
import net.corda.components.examples.pubsub.RunPubSub
import net.corda.libs.configuration.write.factory.CordaWriteServiceFactory
import net.corda.libs.kafka.topic.utils.factory.TopicUtilsFactory
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.LifeCycleEvent
import net.corda.lifecycle.SimpleLifeCycleCoordinator
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

@Component
class Demo @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = TopicUtilsFactory::class)
    private val topicUtilsFactory: TopicUtilsFactory,
    @Reference(service = CordaWriteServiceFactory::class)
    private val cordaWriteServiceFactory: CordaWriteServiceFactory
) : Application {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        const val BATCH_SIZE: Int = 128
        const val TIMEOUT: Long = 10000L
    }

    override fun startup(args: Array<String>) {
        if (args.size != 2) {
            println("Required command line arguments: instanceId topicPrefix")
            exitProcess(1)
        }

        val instanceId = args[0].toInt()
        val topicPrefix = args[1]

        var bootstrapTopics: BootstrapTopics? = null

        val compactedSub = RunCompactedSub(subscriptionFactory)
        val durableSub = RunDurableSub(subscriptionFactory, instanceId)
        val pubsubSub = RunPubSub(subscriptionFactory)
        val publisher = RunPublisher(publisherFactory, instanceId)

        SimpleLifeCycleCoordinator(BATCH_SIZE, TIMEOUT) { event: LifeCycleEvent, _: LifeCycleCoordinator ->
            log.debug("processEvent $event")
            when (event) {

                is StartEvent -> {
                    bootstrapTopics?.start()
                }

                is ConfigCompleteEvent -> {
                    pubsubSub.start()
                    compactedSub.start()
                    durableSub.start()
                    publisher.start()
                }

                is StopEvent -> {
                    pubsubSub.stop()
                    compactedSub.stop()
                    durableSub.stop()
                    publisher.stop()
                }
                else -> {
                    log.error("$event unexpected!")
                }
            }
        }.let {
            bootstrapTopics = BootstrapTopics(it, topicUtilsFactory, cordaWriteServiceFactory, topicPrefix)
            it.start()
        }
    }

    override fun shutdown() {
        log.info("Stopping application")
    }
}
