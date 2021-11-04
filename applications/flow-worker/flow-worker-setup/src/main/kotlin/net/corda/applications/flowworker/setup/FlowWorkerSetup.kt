package net.corda.applications.flowworker.setup

import net.corda.applications.common.ConfigHelper
import net.corda.applications.common.ConfigHelper.Companion.SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH
import net.corda.applications.common.ConfigHelper.Companion.getBootstrapConfig
import net.corda.applications.common.ConfigHelper.Companion.getConfigValue
import net.corda.applications.flowworker.setup.helper.getHelloWorldRPCEventRecord
import net.corda.comp.kafka.config.write.KafkaConfigWrite
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.components.examples.publisher.CommonPublisher
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
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
import java.util.*

@Suppress("LongParameterList")
@Component
class FlowWorkerSetup @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = KafkaConfigWrite::class)
    private var configWriter: KafkaConfigWrite,
    @Reference(service = KafkaTopicAdmin::class)
    private var kafkaTopicAdmin: KafkaTopicAdmin,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory
) : Application {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private var lifeCycleCoordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<FlowWorkerSetup>(::eventHandler)

    private var registration: RegistrationHandle? = null
    private var publisher: CommonPublisher? = null
    private var instanceId: Int? = null
    private var configurationFile : File? = null
    private var topicTemplate : File? = null
    private var bootstrapConfig : SmartConfig? = null


    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting flow worker setup tool...")

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        instanceId = parameters.instanceId?.toInt()
        configurationFile = parameters.configurationFile
        topicTemplate = parameters.topicTemplate
        bootstrapConfig =  smartConfigFactory.create(getBootstrapConfig(instanceId))

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
                setupPublisher(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    consoleLogger.info("Publishing RPCRecord")
                    publisher?.publishRecords(listOf(getHelloWorldRPCEventRecord()))?.forEach { it.get() }
                    consoleLogger.info("Published RPCRecord")
                    shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
                } else {
                    publisher?.close()
                }
            }
            is StopEvent -> {
                publisher?.close()
            }
            else -> {
                log.error("$event unexpected!")
            }
        }
    }

    private fun setupPublisher(coordinator: LifecycleCoordinator) {
        consoleLogger.info("Creating publisher")
        val config = bootstrapConfig ?: throw CordaRuntimeException("BootstrapConfig is null")
        publisher = CommonPublisher(coordinatorFactory, publisherFactory, instanceId, config)
        registration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<CommonPublisher>()))
        publisher?.start()
        consoleLogger.info("Publisher created")
    }

    private fun writeConfig(configurationFile: File?) {
        val config = bootstrapConfig ?: throw CordaRuntimeException("BootstrapConfig is null")
        if (configurationFile != null) {
            log.info("Writing config to topic")
            consoleLogger.info("Writing config")
            configWriter.updateConfig(
                getConfigValue(ConfigHelper.SYSTEM_ENV_CONFIG_TOPIC_PATH, ConfigHelper.DEFAULT_CONFIG_TOPIC_VALUE),
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
        kafkaProperties[SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH] = getConfigValue(SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH, "localhost:9092")
        return kafkaProperties
    }

    override fun shutdown() {
        consoleLogger.info("Stopping Flow Worker setup tool")
        log.info("Stopping Flow Worker setup tool")
        lifeCycleCoordinator.stop()
    }
}
