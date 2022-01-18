package net.corda.virtualnode

import com.typesafe.config.ConfigFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.configuration.publish.ConfigPublishService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.createCoordinator
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.packaging.CPI
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.tools.setup.common.ConfigHelper.Companion.SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH
import net.corda.tools.setup.common.ConfigHelper.Companion.getBootstrapConfig
import net.corda.tools.setup.common.ConfigHelper.Companion.getConfigValue
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.util.*

/**
 * Steps
 *
 * * we connect to Kafka
 * * optionally create topics
 * * we *publish* the config to Kafka
 * * then components using the [ConfigurationReadService] will receive the correct Kafka config and can start.
 *
 * The reader side will do similar.
 */
@Suppress("LongParameterList")
@Component(service = [Application::class])
class VirtualNodeCli @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CpiInfoWriteService::class)
    private val cpiInfoWriteService: CpiInfoWriteService,
//    @Reference(service = VirtualNodeInfoWriterComponent::class)
//    private val virtualNodeInfoWriterComponent: VirtualNodeInfoWriterComponent,
    @Reference(service = ConfigPublishService::class)
    private val configPublishService: ConfigPublishService,
    @Reference(service = KafkaTopicAdmin::class)
    private var topicAdmin: KafkaTopicAdmin
) : Application, ConfigPublisher {

    private companion object {
        val log: Logger = contextLogger()
        val console: Logger = LoggerFactory.getLogger("Console")
    }

    private val configPublisherEventHandler = ConfigPublisherEventHandler(this)
    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeCli>(configPublisherEventHandler)
    private var registrationHandle: RegistrationHandle? = null

    private var configurationFile: File? = null
    private var topicTemplate: File? = null
    private var bootstrapConfig: SmartConfig? = null

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        console.info("Starting")
        log.info("Starting")

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)

        // Set the instance id for the components to pick up (eventually - not implemented here).
        val instanceId = parameters.instanceId?.toInt()

        // Custom config file.
        configurationFile = parameters.configurationFile

        // The topics we need to create in Kafka before we can publish
        topicTemplate = parameters.topicTemplate

        // Get a default, hard-coded config.  tl;dr returns bootstrap.servers = localhost:9092
        // TODO - pick up secrets params from startup
        val secretsConfig = ConfigFactory.empty()
        bootstrapConfig = SmartConfigFactory.create(secretsConfig).create(getBootstrapConfig(instanceId))

        // Start the event handler loop.
        coordinator.start()
    }

    override fun shutdown() {
        log.info("Shutting down")
        coordinator.stop()
    }

    /** Forced closure of the application - this is a CLI, so when we've done our thing, exit the process. */
    private fun exitApplication() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    /**
     * Finally... everything is 'UP' and we get called back.
     *
     * Now just read the CLI args and determine whether we're posting [VirtualNodeInfo] or [CPI.Metadata]
     *
     */
    override fun ready() {
        log.debug { "Components all ready" }
        log.debug { "Writing CPI Metadata" }

        val hash = SecureHash("Dummy", "0123456789ABCDEF".toByteArray())
        val id = CPI.Identifier.newInstance("test-cpi-name", "0.0.0", hash)
        val metadata = CPI.Metadata.newInstance(id, hash, emptyList(), "group-policy")
        cpiInfoWriteService.put(metadata)

//        if (thisThing) cpiInfoWriterComponent.put(cpiMetadata)
//        if (thatThing) virtualNodeInfoWriterComponent.put(virtualNodeInfo)

        exitApplication()
    }

    override fun publishConfig() {
        log.debug { "Publish config" }
        val config = bootstrapConfig ?: throw CordaRuntimeException("BootstrapConfig is null")

        if (configurationFile == null)
            return

        configPublishService.updateConfig(
            CONFIG_TOPIC,
            config,
            configurationFile!!.readText()
        )

        log.debug { "Config published" }
    }

    override fun createTopics() {
        log.debug { "Creating topics" }

        if (topicTemplate == null)
            return

        val kafkaProperties = Properties().also {
            it[SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH] =
                getConfigValue(SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH, "localhost:9092")
        }

        topicAdmin.createTopics(kafkaProperties, topicTemplate!!.readText())

        log.debug("Topics created")
    }

    override fun configPublished() {
        log.debug("Config published")

        // We register that we want to wait for ALL of these [Lifecycle] components to come up
        // and set ALL of their statuses to UP, which then raises a [RegistrationStatusChangeEvent]
        // when they are all up, we finally peform the CLI action via [onReady] below.
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
//                LifecycleCoordinatorName.forComponent<VirtualNodeInfoWriterComponent>(),
                LifecycleCoordinatorName.forComponent<CpiInfoWriteService>()
            )
        )

//        virtualNodeInfoWriterComponent.start()
        cpiInfoWriteService.start()
    }

    override fun done() {
        log.debug("Done, stopping...")

//        virtualNodeInfoWriterComponent.stop()
        cpiInfoWriteService.stop()
    }
}
