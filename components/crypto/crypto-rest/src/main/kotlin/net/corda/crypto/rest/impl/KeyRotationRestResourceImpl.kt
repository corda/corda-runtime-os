package net.corda.crypto.rest.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.rest.KeyRotationRestResource
import net.corda.crypto.rest.response.KeyRotationResponse
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationStatus
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.rest.PluggableRestResource
import net.corda.rest.messagebus.MessageBusUtils.tryWithExceptionHandling
import net.corda.rest.response.ResponseEntity
import net.corda.schema.Schemas.Crypto.REKEY_MESSAGE_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.annotations.VisibleForTesting
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList")
@Component(service = [PluggableRestResource::class])
class KeyRotationRestResourceImpl @Activate constructor(
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : KeyRotationRestResource, PluggableRestResource<KeyRotationRestResource>, Lifecycle {


    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val configKeys = setOf(
        ConfigKeys.MESSAGING_CONFIG,
        ConfigKeys.STATE_MANAGER_CONFIG,
    )

    private var publishToKafka: Publisher? = null
    private var stateManager: StateManager? = null
    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<KeyRotationStatus>()
    private val deserializer = cordaAvroSerializationFactory.createAvroDeserializer({}, KeyRotationStatus::class.java)

    override val targetInterface: Class<KeyRotationRestResource> = KeyRotationRestResource::class.java
    override val protocolVersion: Int = platformInfoProvider.localWorkerPlatformVersion

    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator<KeyRotationRestResource>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
    )
    private var subscriptionRegistrationHandle: RegistrationHandle? = null
    private var isUp = false
    override val isRunning get() = publishToKafka != null && isUp

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Handling KeyRotationRestResource event, $event.")

        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
                if (!isUp) {
                    isUp = true
                    signalUpStatus()
                }
            }

            is RegistrationStatusChangeEvent -> {
                configurationReadService.registerComponentForUpdates(lifecycleCoordinator, configKeys)
                if (event.status == LifecycleStatus.UP) {
                    lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }

            is ConfigChangedEvent -> {
                initialise(event.config)

                val stateManagerConfig = event.config.getConfig(ConfigKeys.STATE_MANAGER_CONFIG)

                stateManager?.stop()
                stateManager = stateManagerFactory.create(stateManagerConfig).also { it.start() }
                logger.debug("State manager created and started ${stateManager!!.name}")
            }

            is StopEvent -> {
                subscriptionRegistrationHandle?.close()
                stateManager?.stop()
                publishToKafka?.close()
            }

            else -> {
                logger.error("Unexpected event $event!")
            }
        }
    }

    private fun signalUpStatus() {
        if (isRunning) {
            lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    @VisibleForTesting
    fun initialise(config: Map<String, SmartConfig>) {
        val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)

        // Initialise publisher with messaging config
        publishToKafka?.close()
        val newPublisher =
            publisherFactory.createPublisher(PublisherConfig("KeyRotationRestResource", false), messagingConfig)
        newPublisher.start()
        publishToKafka = newPublisher
    }

    override fun getKeyRotationStatus(requestId: String): List<Pair<String, String>> {
        val sM = tryWithExceptionHandling(logger, "retrieve key rotation status") {
            checkNotNull(stateManager)
        }

        val entries = sM.get(listOf(requestId))
        val result = mutableListOf<Pair<String, String>>()
        entries.forEach { entry ->
            val keyRotationStatus = deserializer.deserialize(entry.value.value)!!
            result.add(keyRotationStatus.oldParentKeyAlias to keyRotationStatus.requestId.toString())
        }
        return result
    }

    override fun startKeyRotation(oldKeyAlias: String, newKeyAlias: String): ResponseEntity<KeyRotationResponse> {
        val pTK = tryWithExceptionHandling(logger, "start key rotation") {
            checkNotNull(publishToKafka)
        }
        val sM = tryWithExceptionHandling(logger, "retrieve key rotation status") {
            checkNotNull(stateManager)
        }

        return doKeyRotation(
            oldKeyAlias,
            newKeyAlias,
            serializeStatus = { serializer.serialize(it) },
            publishStates = { sM.create(it) },
            publishRequests = { pTK.publish(it) }
        )
    }
}

/*
 * do the start key rotation operation
 *
 * @param oldKeyAlias alias to replace
 * @param newKeyAlias alias to use
 * @param serializeStatus callback to turn a status message into a byte array, if possible, or null
 * @param publishStates callback to publish a list of states (to state manager, in production)
 * @param publishKafka callback to publish a list of kafka messages
 *
 * This is a top level function to make it easy to test without bothering with lifecycle and OSGi.
 */
fun doKeyRotation(
    oldKeyAlias: String,
    newKeyAlias: String,
    serializeStatus: (KeyRotationStatus) -> ByteArray?,
    publishStates: ((List<State>) -> Unit),
    publishRequests: ((List<Record<String, KeyRotationRequest>>) -> Unit)
): ResponseEntity<KeyRotationResponse> {
    // We cannot validate oldKeyAlias or newKeyAlias early here on the client side of the RPC since
    // those values are considered sensitive.

    val requestId = UUID.randomUUID().toString()
    val keyRotationRequest = KeyRotationRequest(
        requestId,
        KeyType.UNMANAGED,
        oldKeyAlias,
        newKeyAlias,
        null,
        null
    )

    val status = KeyRotationStatus(
        requestId,
        KeyType.UNMANAGED,
        oldKeyAlias,
        newKeyAlias,
        null,
        null,
        0,
        0,
        0,
        Instant.now(),
        Instant.now()
    )

    publishRequests(listOf(Record(REKEY_MESSAGE_TOPIC, requestId, keyRotationRequest)))

    val serialized = serializeStatus(status)
    checkNotNull(serialized)
    publishStates(listOf(State(requestId, serialized, 1, Metadata(), Instant.now())))
    return ResponseEntity.accepted(KeyRotationResponse(requestId, oldKeyAlias, newKeyAlias))
}
