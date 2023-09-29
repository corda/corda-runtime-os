package net.corda.interop.identity.write.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.interop.identity.write.InteropIdentityWriteService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.interop.identity.registry.InteropIdentityRegistryService
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.UUID


@Suppress("ForbiddenComment", "LongParameterList")
@Component(service = [InteropIdentityWriteService::class])
class InteropIdentityWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = InteropIdentityRegistryService::class)
    private val interopIdentityRegistryService: InteropIdentityRegistryService,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = HSMRegistrationClient::class)
    private val hsmRegistrationClient: HSMRegistrationClient
) : InteropIdentityWriteService, LifecycleEventHandler {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val CLIENT_ID = "INTEROP_IDENTITY_WRITER"
        const val CREATE_ID = "CREATE_ID"
    }

    private val coordinatorName = LifecycleCoordinatorName.forComponent<InteropIdentityWriteService>()
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, this)

    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    private val publisher: AtomicReference<Publisher?> = AtomicReference()

    private val sessionKeyGenerator = SessionKeyGenerator(cryptoOpsClient, keyEncodingService, hsmRegistrationClient)
    private val interopIdentityProducer = InteropIdentityProducer(publisher)
    private val hostedIdentityProducer = HostedIdentityProducer(publisher, sessionKeyGenerator)
    private val membershipInfoProducer = MembershipInfoProducer(publisher, sessionKeyGenerator)
    private val interopGroupPolicyProducer = InteropGroupPolicyProducer(publisher)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun addInteropIdentity(vNodeShortHash: ShortHash, identity: InteropIdentity) {
        writeMemberInfoTopic(vNodeShortHash, identity)
        writeInteropIdentityTopic(vNodeShortHash, identity)

        if (vNodeShortHash == identity.owningVirtualNodeShortHash) {
            writeHostedIdentitiesTopic(vNodeShortHash, identity)
        }
    }

    override fun publishGroupPolicy(groupId: String, groupPolicy: String): String {
        val json = ObjectMapper().readTree(groupPolicy) as ObjectNode

        require(json.hasNonNull("groupId")) {
            "Invalid group policy, 'groupId' field is missing or null"
        }

        require(json.get("groupId").isTextual) {
            "Invalid group policy, 'groupId' field is not a text node."
        }

        val finalGroupId = when(val inputGroupId = json.get("groupId").asText()) {
            "CREATE_ID" -> UUID.randomUUID()
            else -> try {
                UUID.fromString(inputGroupId)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid group policy, 'groupId' field is not a valid UUID.")
            }
        }

        json.set<TextNode>("groupId", JsonNodeFactory.instance.textNode(finalGroupId.toString()))
        val finalGroupPolicy = json.toString()

        interopGroupPolicyProducer.publishInteropGroupPolicy(finalGroupId.toString(), finalGroupPolicy)
        
        return finalGroupId.toString()
    }

    private fun writeMemberInfoTopic(vNodeShortHash: ShortHash, identity: InteropIdentity) {
        val cacheView = interopIdentityRegistryService.getVirtualNodeRegistryView(vNodeShortHash)

        // If the new interop identity will become the owned one use that. Otherwise, retrieve the existing one from the registry.
        val ownedInteropIdentity = if (identity.owningVirtualNodeShortHash == vNodeShortHash) {
            identity
        } else {
            checkNotNull(cacheView.getOwnedIdentity(identity.groupId)) {
                "The interop group ${identity.groupId} does not contain an interop identity for holding identity $vNodeShortHash."
            }
        }

        // This may be null when the owning virtual node is hosted on a different cluster
        val realHoldingIdentity =
            virtualNodeInfoReadService.getByHoldingIdentityShortHash(identity.owningVirtualNodeShortHash)?.holdingIdentity

        // If the interop identity is owned by the virtual node, we need to ensure the real holding identity is known
        if (identity.owningVirtualNodeShortHash == vNodeShortHash) {
            checkNotNull(realHoldingIdentity) {
                "Could not find real holding identity of virtual node '$vNodeShortHash'."
            }
        }

        membershipInfoProducer.publishMemberInfo(realHoldingIdentity, ownedInteropIdentity, listOf(identity))
    }

    private fun writeInteropIdentityTopic(vNodeShortHash: ShortHash, identity: InteropIdentity) {
        interopIdentityProducer.publishInteropIdentity(vNodeShortHash, identity)
    }

    private fun writeHostedIdentitiesTopic(vNodeShortHash: ShortHash, identity: InteropIdentity) {
        hostedIdentityProducer.publishHostedInteropIdentity(vNodeShortHash, identity)
    }

    override fun start() {
        // Use debug rather than info
        log.info("Component starting")
        coordinator.start()
    }

    override fun stop() {
        // Use debug rather than info
        log.info("Component stopping")
        coordinator.stop()
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is StopEvent -> onStopEvent()
            is ConfigChangedEvent -> onConfigChangeEvent(event, coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            else -> {
                log.error("Unexpected event: '$event'")
            }
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration = coordinator.followStatusChangesByName(setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
        ))
    }

    private fun onStopEvent() {
        configSubscription?.close()
        configSubscription = null
        registration?.close()
        registration = null
        publisher.get()?.close()
        publisher.set(null)
    }

    private fun onConfigChangeEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val config = event.config[ConfigKeys.MESSAGING_CONFIG] ?: return

        // TODO: Use debug rather than info
        log.info("Processing config update")

        coordinator.updateStatus(LifecycleStatus.DOWN)

        publisher.get()?.close()
        publisher.set(publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), config))

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription = configurationReadService.registerComponentForUpdates(coordinator, setOf(
                ConfigKeys.MESSAGING_CONFIG
            ))
        } else {
            configSubscription?.close()
            configSubscription = null
            publisher.get()?.close()
            publisher.set(null)
        }
    }
}
