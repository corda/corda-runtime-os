package net.corda.interop.identity.write.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
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


@Suppress("ForbiddenComment", "TooManyFunctions")
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
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService
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

    private val interopIdentityProducer = InteropIdentityProducer(publisher)
    private val hostedIdentityProducer = HostedIdentityProducer(publisher)
    private val membershipInfoProducer = MembershipInfoProducer(publisher)
    private val interopGroupPolicyProducer = InteropGroupPolicyProducer(publisher)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun addInteropIdentity(vNodeShortHash: ShortHash, identity: InteropIdentity) {
        writeMemberInfoTopic(vNodeShortHash, identity)
        writeInteropIdentityTopic(vNodeShortHash, identity)

        if (vNodeShortHash == identity.owningVirtualNodeShortHash) {
            writeHostedIdentitiesTopic(identity)
        }
    }

    override fun updateInteropIdentityEnablement(
        vNodeShortHash: ShortHash,
        identity: InteropIdentity,
        enablementState: Boolean
    ) {
        val updatedIdentity = InteropIdentity(
            x500Name = identity.x500Name,
            groupId = identity.groupId,
            owningVirtualNodeShortHash = identity.owningVirtualNodeShortHash,
            facadeIds = identity.facadeIds,
            applicationName = identity.applicationName,
            endpointUrl = identity.endpointUrl,
            endpointProtocol = identity.endpointProtocol,
            enabled = enablementState
        )

        writeInteropIdentityTopic(vNodeShortHash, updatedIdentity)
    }

    override fun removeInteropIdentity(vNodeShortHash: ShortHash, identity: InteropIdentity) {
        clearMemberInfoTopic(vNodeShortHash, identity.shortHash)
        clearInteropIdentityTopic(vNodeShortHash, identity.shortHash)

        if (vNodeShortHash == identity.owningVirtualNodeShortHash) {
            clearHostedIdentitiesTopic(identity.shortHash)
        }
    }

    override fun publishGroupPolicy(groupPolicy: String): UUID {
        val json = ObjectMapper().readTree(groupPolicy) as ObjectNode

        require(json.hasNonNull("groupId")) {
            "Invalid group policy, 'groupId' field is missing or null"
        }

        require(json.get("groupId").isTextual) {
            "Invalid group policy, 'groupId' field is not a text node."
        }

        val groupId = when(val inputGroupId = json.get("groupId").asText()) {
            CREATE_ID -> UUID.randomUUID()
            else -> try {
                UUID.fromString(inputGroupId)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid group policy, 'groupId' field is not a valid UUID.")
            }
        }

        json.set<TextNode>("groupId", JsonNodeFactory.instance.textNode(groupId.toString()))
        val finalGroupPolicy = json.toString()

        interopGroupPolicyProducer.publishInteropGroupPolicy(groupId, finalGroupPolicy)
        
        return groupId
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

    private fun clearMemberInfoTopic(vNodeShortHash: ShortHash, interopIdentityShortHash: ShortHash) {
        val cacheView = interopIdentityRegistryService.getVirtualNodeRegistryView(vNodeShortHash)

        val identityToRemove = checkNotNull(cacheView.getIdentityWithShortHash(interopIdentityShortHash)) {
            "No interop identity with short hash $interopIdentityShortHash"
        }

        val owningIdentity = checkNotNull(cacheView.getOwnedIdentity(identityToRemove.groupId)) {
            "No identity owned by virtual node $vNodeShortHash in group ${identityToRemove.groupId}"
        }

        membershipInfoProducer.clearMemberInfo(owningIdentity, identityToRemove)
    }

    private fun writeInteropIdentityTopic(vNodeShortHash: ShortHash, identity: InteropIdentity) {
        interopIdentityProducer.publishInteropIdentity(vNodeShortHash, identity)
    }

    private fun clearInteropIdentityTopic(vNodeShortHash: ShortHash, interopIdentityShortHash: ShortHash) {
        interopIdentityProducer.clearInteropIdentity(vNodeShortHash, interopIdentityShortHash)
    }

    private fun writeHostedIdentitiesTopic(identity: InteropIdentity) {
        hostedIdentityProducer.publishHostedInteropIdentity(identity)
    }

    private fun clearHostedIdentitiesTopic(interopIdentityShortHash: ShortHash) {
        hostedIdentityProducer.clearHostedInteropIdentity(interopIdentityShortHash)
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
