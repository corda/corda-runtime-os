package net.corda.components.examples.bootstrap.topics


import net.corda.comp.kafka.config.write.KafkaConfigWrite
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.lifecycle.LifeCycle
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.LifeCycleEvent
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.*

object ConfigCompleteEvent : LifeCycleEvent

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
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override var isRunning: Boolean = false

    override fun start() {
        isRunning = true

        val kafkaConnectionProperties = Properties()
        kafkaConnectionProperties.load(FileInputStream(kafkaConnection))
        val topic = topicAdmin.createTopic(kafkaConnectionProperties, topicTemplate.readText())
        configWriter.updateConfig(
            topic.getString("topicName"),
            kafkaConnectionProperties,
            configurationFile.readText()
        )

        lifeCycleCoordinator.postEvent(ConfigCompleteEvent)
        isRunning = false
    }

    override fun stop() {
        isRunning = false
        log.info("Stopping topic bootstrapper")
    }
}