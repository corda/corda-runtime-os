package net.corda.crypto.rest.impl

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.config.impl.ALIAS
import net.corda.crypto.config.impl.HSM
import net.corda.crypto.config.impl.WRAPPING_KEYS
import net.corda.crypto.rest.KeyRotationRestResource
import net.corda.crypto.rest.response.KeyRotationResponse
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyType
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.rest.response.ResponseEntity
import net.corda.schema.Schemas.Crypto.REKEY_MESSAGE_TOPIC
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

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
) : KeyRotationRestResource, PluggableRestResource<KeyRotationRestResource>, Lifecycle {
    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val uploadTopic = REKEY_MESSAGE_TOPIC
    private var publisher: Publisher? = null
    private lateinit var unmanagedWrappingKeyAliases: Set<String>

    override val targetInterface: Class<KeyRotationRestResource> = KeyRotationRestResource::class.java
    override val protocolVersion: Int = platformInfoProvider.localWorkerPlatformVersion

    private val requestId = UUID.randomUUID().toString()
    override fun initialise(config: Map<String, SmartConfig>) {
        val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        val cryptoConfig = config.getConfig(ConfigKeys.CRYPTO_CONFIG)

        // Initialise publisher with messaging config
        publisher?.close()
        publisher = publisherFactory.createPublisher(PublisherConfig(requestId), messagingConfig)

        // Initialise unmanaged wrapping keys from the crypto config
        val keysList: List<Config> = cryptoConfig.getConfig(HSM).getConfigList(WRAPPING_KEYS)
        unmanagedWrappingKeyAliases =
            keysList.map {
                it.getString(ALIAS)
            }.toSet()
    }


    override fun getKeyRotationStatus(): List<Pair<String, List<String>>> {
        TODO("Not yet implemented")
    }

    override fun startKeyRotation(
        oldKeyAlias: String,
        newKeyAlias: String,
        simulate: Boolean,
        timeToLive: Int,
        limit: Int
    ): ResponseEntity<KeyRotationResponse> {
        if (publisher == null) {
            throw ServiceUnavailableException("Key rotation resource has not been initialised.")
        }

        if (oldKeyAlias !in unmanagedWrappingKeyAliases)
            throw InvalidInputDataException("Old key alias is not part of the configuration.")

        if (newKeyAlias !in unmanagedWrappingKeyAliases)
            throw InvalidInputDataException("New key alias is not part of the configuration.")

        // We need to create a Record that tells Crypto processor to do key rotation
        // Do we need to start the publisher? FlowRestResource is not starting its publisher for some reason
        publisher!!.start()

        val keyRotationRequest = KeyRotationRequest(
            requestId,
            KeyType.UNMANAGED,
            oldKeyAlias,
            newKeyAlias,
            null,
            null,
            simulate,
            timeToLive,
            limit
        )

        publisher!!.publish(listOf(Record(uploadTopic, requestId, keyRotationRequest)))

        return ResponseEntity.accepted(KeyRotationResponse(requestId, oldKeyAlias, newKeyAlias))
    }

    private var isUp = false
    override val isRunning get() = publisher != null && isUp
    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator<KeyRotationRestResource>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
    )

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
                if (!isUp) {
                    isUp = true
                    signalUpStatus()
                }
            }
            is RegistrationStatusChangeEvent -> {
                configurationReadService.registerComponentForUpdates(
                    lifecycleCoordinator,
                    setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
                )
                if (event.status == LifecycleStatus.UP) {
                    lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            is ConfigChangedEvent -> {
                initialise(event.config)
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }

    private fun signalUpStatus() {
        if (isRunning) {
            lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        publisher?.close()
        lifecycleCoordinator.stop()
    }
}
