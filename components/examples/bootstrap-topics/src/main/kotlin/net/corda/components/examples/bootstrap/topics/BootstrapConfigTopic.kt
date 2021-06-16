package net.corda.components.examples.bootstrap.topics


import net.corda.comp.kafka.config.write.KafkaConfigWrite
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.lifecycle.LifeCycle
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.LifeCycleEvent
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import java.io.File
import java.io.FileInputStream
import java.util.*

object TopicsCreatedEvent : LifeCycleEvent

@Suppress("LongParameterList")
@Component
class BootstrapConfigTopic(
    private val lifeCycleCoordinator: LifeCycleCoordinator,
    private var topicAdmin: KafkaTopicAdmin,
    private var configWriter: KafkaConfigWrite,
    private var kafkaConnection: File,
    private var topicTemplate: File,
    private var configurationFile: File
    ) : LifeCycle {

    private companion object {
        private val log: Logger = contextLogger()
    }

    override var isRunning: Boolean = false

    override fun start() {
        isRunning = true
        val kafkaConnectionProperties = Properties()
        kafkaConnectionProperties.load(FileInputStream(kafkaConnection))
        log.info("Creating config topic")
        val topic = topicAdmin.createTopic(kafkaConnectionProperties, topicTemplate.readText())

        log.info("Writing config to topic")
        configWriter.updateConfig(
            topic.getString("topicName"),
            kafkaConnectionProperties,
            configurationFile.readText()
        )
        log.info("Signaling topics created event")
        lifeCycleCoordinator.postEvent(TopicsCreatedEvent)
        isRunning = false
    }

    override fun stop() {
        isRunning = false
        log.info("Stopping topic bootstrapper")
    }
}