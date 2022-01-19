package net.corda.applications.flowworker.setup

import com.typesafe.config.ConfigFactory
import net.corda.applications.flowworker.setup.helper.getHelloWorldRPCEventRecords
import net.corda.applications.flowworker.setup.helper.getHelloWorldScheduleCleanupEvent
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.configuration.publish.ConfigPublishService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.tools.setup.common.ConfigHelper.Companion.SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH
import net.corda.tools.setup.common.ConfigHelper.Companion.getBootstrapConfig
import net.corda.tools.setup.common.ConfigHelper.Companion.getConfigValue
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.util.Properties

@Suppress("LongParameterList")
@Component
class FlowWorkerSetup @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigPublishService::class)
    private var configPublish: ConfigPublishService,
    @Reference(service = KafkaTopicAdmin::class)
    private var kafkaTopicAdmin: KafkaTopicAdmin
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private var lifeCycleCoordinator: LifecycleCoordinator =
        coordinatorFactory.createCoordinator<FlowWorkerSetup>(::eventHandler)

    private var publisher: Publisher? = null
    private var instanceId: Int? = null
    private var configurationFile: File? = null
    private var topicTemplate: File? = null
    private var bootstrapConfig: SmartConfig? = null
    private var scheduleCleanup = false


    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting flow worker setup tool...")

        val parameters = FlowWorkerSetupParams()
        CommandLine(parameters).parseArgs(*args)
        instanceId = parameters.defaultParams.instanceId?.toInt()
        configurationFile = parameters.defaultParams.configurationFile
        topicTemplate = parameters.defaultParams.topicTemplate
        scheduleCleanup = parameters.scheduleCleanup
        // TODO - pick up secrets params from startup
        val secretsConfig = ConfigFactory.empty()
        bootstrapConfig = SmartConfigFactory.create(secretsConfig).create(getBootstrapConfig(instanceId))

        lifeCycleCoordinator.start()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("LifecycleEvent received: $event")
        when (event) {
            is StartEvent -> {
                createTopics(topicTemplate)
                coordinator.postEvent(TopicsCreated())
            }
            is TopicsCreated -> {
                writeConfig(configurationFile)
                coordinator.postEvent(ConfigWritten())
            }
            is ConfigWritten -> {
                setupPublisher()
            }
            is PublisherBuilt -> {
                publishEvents()
            }
            is StopEvent -> {
                publisher?.close()
            }
            else -> {
                log.error("$event unexpected!")
            }
        }
    }

    private fun publishEvents() {
        consoleLogger.info("Publishing RPCRecord")
        if (!scheduleCleanup) {
            publisher?.publish(getHelloWorldRPCEventRecords())?.forEach { it.get() }
        } else {
            publisher?.publish(listOf(getHelloWorldScheduleCleanupEvent()))?.forEach { it.get() }
        }
        consoleLogger.info("Published RPCRecord")
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    private fun setupPublisher() {
        consoleLogger.info("Creating publisher")
        val config = bootstrapConfig ?: throw CordaRuntimeException("BootstrapConfig is null")
        publisher = publisherFactory.createPublisher(PublisherConfig("flow-setup-publisher", instanceId), config)
        publisher?.start()
        consoleLogger.info("Publisher created")
        lifeCycleCoordinator.postEvent(PublisherBuilt())
    }

    private fun writeConfig(configurationFile: File?) {
        val config = bootstrapConfig ?: throw CordaRuntimeException("BootstrapConfig is null")
        if (configurationFile != null) {
            log.info("Writing config to topic")
            consoleLogger.info("Writing config")
            configPublish.updateConfig(
                CONFIG_TOPIC,
                config,
                configurationFile.readText()
            )
            log.info("Write of config to topic completed")
            consoleLogger.info("Write of config completed")
        }
    }

    private fun createTopics(topicTemplate: File?) {
        if (topicTemplate != null) {
            consoleLogger.info("Creating topics")
            log.info("Creating topics")
            kafkaTopicAdmin.createTopics(getKafkaProperties(), topicTemplate.readText())
            log.info("Topics created")
            consoleLogger.info("Topic creation completed")
        }
    }

    private fun getKafkaProperties(): Properties {
        val kafkaProperties = Properties()
        kafkaProperties[SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH] =
            getConfigValue(SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH, "localhost:9092")
        return kafkaProperties
    }

    override fun shutdown() {
        consoleLogger.info("Stopping Flow Worker setup tool")
        log.info("Stopping Flow Worker setup tool")
        lifeCycleCoordinator.stop()
    }
}
