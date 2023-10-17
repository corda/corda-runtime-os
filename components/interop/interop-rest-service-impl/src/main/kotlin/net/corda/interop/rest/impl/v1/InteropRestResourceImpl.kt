package net.corda.interop.rest.impl.v1

import com.fasterxml.jackson.databind.node.ArrayNode
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.interop.core.Utils
import net.corda.interop.group.policy.read.InteropGroupPolicyReadService
import net.corda.interop.identity.registry.InteropIdentityRegistryService
import net.corda.interop.identity.write.InteropIdentityWriteService
import net.corda.libs.interop.endpoints.v1.InteropRestResource
import net.corda.libs.interop.endpoints.v1.types.CreateInteropIdentityRest
import net.corda.libs.interop.endpoints.v1.types.ExportInteropIdentityRest
import net.corda.libs.interop.endpoints.v1.types.GroupPolicy
import net.corda.libs.interop.endpoints.v1.types.ImportInteropIdentityRest
import net.corda.libs.interop.endpoints.v1.types.InteropIdentityResponse
import net.corda.libs.interop.endpoints.v1.types.P2pParameters
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.group.policy.validation.InteropGroupPolicyValidator
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.json.serialization.jacksonObjectMapper
import net.corda.rest.response.ResponseEntity
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import net.corda.rest.ResponseCode

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [PluggableRestResource::class])
internal class InteropRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = InteropIdentityRegistryService::class)
    private val interopIdentityRegistryService: InteropIdentityRegistryService,
    @Reference(service = InteropIdentityWriteService::class)
    private val interopIdentityWriteService: InteropIdentityWriteService,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = InteropGroupPolicyReadService::class)
    private val interopGroupPolicyReadService: InteropGroupPolicyReadService,
    @Reference(service = InteropGroupPolicyValidator::class)
    private val interopGroupPolicyValidator: InteropGroupPolicyValidator,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider
) : InteropRestResource, PluggableRestResource<InteropRestResource>, Lifecycle {

    private companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.REST_CONFIG)
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
        private val mapper =  jacksonObjectMapper()
    }

    /**
     * These are identical at the moment, but this may not be the case in future revisions of corda
     * Separate methods have been created so that it is obvious to future maintainers which is required
     */
    private fun VirtualNodeInfo.getVNodeShortHash() = this.holdingIdentity.shortHash
    private fun VirtualNodeInfo.getHoldingIdentityShortHash() = this.holdingIdentity.shortHash

    // RestResource values
    override val targetInterface: Class<InteropRestResource> = InteropRestResource::class.java
    override val protocolVersion = 1

    override fun getInterOpGroups(holdingIdentityShortHash: String): Map<UUID, String> {
        val validHoldingIdentityShortHash = validateShortHash(holdingIdentityShortHash)
        val vNodeInfo = getAndValidateVirtualNodeInfoByShortHash(validHoldingIdentityShortHash)
        val registryView = interopIdentityRegistryService.getVirtualNodeRegistryView(vNodeInfo.getVNodeShortHash())
        val groupIds = registryView.getIdentities().map { it.groupId }.toSet()
        return groupIds.associate {
            Pair(UUID.fromString(it), interopGroupPolicyReadService.getGroupPolicy(it) ?: "")
        }
    }

    private fun getAndValidateVirtualNodeInfoByShortHash(holdingIdentityShortHash: ShortHash): VirtualNodeInfo {
        return virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
            ?: throw InvalidInputDataException(
                "No virtual node found with short hash $holdingIdentityShortHash."
            )
    }

    private fun validateUUID(
        uuidString: String,
        lazyMessage: () -> String = { "'$uuidString' is not a valid UUID" }
    ): UUID {
        return try {
            UUID.fromString(uuidString)
        } catch (e: Exception) {
            throw InvalidInputDataException(lazyMessage())
        }
    }

    private fun validateShortHash(
        shortHashString: String,
        lazyMessage: () -> String = { "'$shortHashString' is not a valid short hash." }
    ): ShortHash {
        return try {
            ShortHash.parse(shortHashString)
        } catch (e: Exception) {
            throw InvalidInputDataException(lazyMessage())
        }
    }

    private fun getGroupIdFieldFromGroupPolicy(groupPolicyString: String): String {
        val groupPolicyJson = mapper.readTree(groupPolicyString)

        check(groupPolicyJson.has("groupId")) {
            "Malformed group policy json. Group ID field missing from policy."
        }

        check(groupPolicyJson["groupId"].isTextual) {
            "Malformed group policy json. Group ID field is present but is not a text node."
        }

        return groupPolicyJson["groupId"].asText()
    }

    @Suppress("ForbiddenComment")
    override fun createInterOpIdentity(
        createInteropIdentityRestRequest: CreateInteropIdentityRest.Request,
        holdingIdentityShortHash: String
    ): CreateInteropIdentityRest.Response {
        val validHoldingIdentityShortHash = validateShortHash(holdingIdentityShortHash)
        val vNodeInfo = getAndValidateVirtualNodeInfoByShortHash(validHoldingIdentityShortHash)
        val vNodeShortHash = vNodeInfo.getVNodeShortHash()
        val registryView = interopIdentityRegistryService.getVirtualNodeRegistryView(vNodeShortHash)

        if (interopIdentityRegistryService
                .getVirtualNodeRegistryView(validHoldingIdentityShortHash)
                .getIdentitiesByApplicationName(createInteropIdentityRestRequest.applicationName).isNotEmpty()
        ) {
            throw InvalidInputDataException(
                "Interop identity already present with application name '${createInteropIdentityRestRequest.applicationName}'."
            )
        }

        val ownedInteropIdentityX500 = MemberX500Name(
            createInteropIdentityRestRequest.applicationName,
            vNodeInfo.holdingIdentity.x500Name.locality,
            vNodeInfo.holdingIdentity.x500Name.country
        ).toString()
        val json = mapper.writeValueAsString(createInteropIdentityRestRequest.groupPolicy)
        interopGroupPolicyValidator.validateGroupPolicy(json)

        try {
            MemberX500Name.parse(ownedInteropIdentityX500)
        } catch (e: Exception) {
            throw InvalidInputDataException(
                "X500 name \"$ownedInteropIdentityX500\" could not be parsed. Cause: ${e.message}"
            )
        }

        val groupIdField = try {
            getGroupIdFieldFromGroupPolicy(json)
        } catch (e: Exception) {
            throw InvalidInputDataException(e.message!!)
        }

        if (groupIdField == "CREATE_ID") {
            if (!createInteropIdentityRestRequest.members.isNullOrEmpty()) {
                throw InvalidInputDataException(
                    "Cannot import members when creating a new interop group."
                )
            }
        } else if (groupIdField == vNodeInfo.holdingIdentity.groupId) {
            throw InvalidInputDataException(
                "Cannot use the groupId of your own identity during the creation of interop identity."
            )
        } else {
            validateUUID(groupIdField) {
                "Malformed group policy. Group ID must be a valid uuid or 'CREATE_ID', got: $groupIdField"
            }
            if (registryView.getOwnedIdentities(groupIdField).isNotEmpty()) {
                throw InvalidInputDataException(
                    "Virtual node $vNodeShortHash already has an interop identity in interop group $groupIdField."
                )
            }
        }

        val interopGroupId = interopIdentityWriteService.publishGroupPolicy(
            groupIdField,
            json
        )

        val groupReader = membershipGroupReaderProvider.getGroupReader(vNodeInfo.holdingIdentity)
        val owningIdentityMemberInfo = checkNotNull(groupReader.lookup(vNodeInfo.holdingIdentity.x500Name)) {
            "Failed to read member info for identity ${vNodeInfo.holdingIdentity.x500Name}"
        }

        val owningIdentityMemberContext = owningIdentityMemberInfo.memberProvidedContext.entries.associate { entry ->
            entry.key to entry.value
        }

        val endpointUrlKey = String.format(MemberInfoExtension.URL_KEY, "0")
        val protocolVersionKey = String.format(MemberInfoExtension.PROTOCOL_VERSION, "0")

        val endpointUrl = checkNotNull(owningIdentityMemberContext[endpointUrlKey]) {
            "Failed to get endpoint URL from owning identity member context, no entry with key $endpointUrlKey"
        }

        val endpointProtocol = checkNotNull(owningIdentityMemberContext[protocolVersionKey]) {
            "Failed to get endpoint protocol version from owning identity member context, no entry with key $protocolVersionKey"
        }

        // Create the owned interop identity
        interopIdentityWriteService.addInteropIdentity(
            vNodeInfo.getVNodeShortHash(),
            InteropIdentity(
                groupId = interopGroupId,
                x500Name = ownedInteropIdentityX500,
                owningVirtualNodeShortHash = vNodeInfo.getVNodeShortHash(),
                facadeIds = facadeIds(),
                applicationName = createInteropIdentityRestRequest.applicationName,
                endpointUrl = endpointUrl,
                endpointProtocol = endpointProtocol,
                enabled = true
            )
        )

        // If any exported members are present, import them
        createInteropIdentityRestRequest.members?.forEach { member ->
            interopIdentityWriteService.addInteropIdentity(
                vNodeInfo.getVNodeShortHash(),
                InteropIdentity(
                    groupId = interopGroupId,
                    x500Name = member.x500Name,
                    owningVirtualNodeShortHash = ShortHash.of(member.owningIdentityShortHash),
                    facadeIds = member.facadeIds.map { FacadeId.of(it) },
                    applicationName = MemberX500Name.parse(member.x500Name).organization,
                    endpointUrl = member.endpointUrl,
                    endpointProtocol = member.endpointProtocol,
                    enabled = true
                )
            )
        }

        logger.info("InteropIdentity created.")

        return CreateInteropIdentityRest.Response(
            Utils.computeShortHash(ownedInteropIdentityX500, interopGroupId).toString()
        )
    }

    private fun updateInteropIdentityEnablement(
        holdingIdentityShortHash: String,
        interopIdentityShortHash: String,
        newState: Boolean
    ) {
        val validHoldingIdentityShortHash = validateShortHash(holdingIdentityShortHash)
        val validInteropIdentityShortHash = validateShortHash(interopIdentityShortHash)

        val vNodeInfo = getAndValidateVirtualNodeInfoByShortHash(validHoldingIdentityShortHash)
        val vNodeShortHash = vNodeInfo.getVNodeShortHash()

        val registryView = interopIdentityRegistryService.getVirtualNodeRegistryView(vNodeShortHash)

        val identityToDisable = registryView.getIdentityWithShortHash(validInteropIdentityShortHash) ?:
        throw InvalidInputDataException(
            "No interop identity with short hash '$validInteropIdentityShortHash' found for holding " +
                    "identity '$validHoldingIdentityShortHash'."
        )

        if (identityToDisable.enabled != newState) {
            interopIdentityWriteService.updateInteropIdentityEnablement(vNodeShortHash, identityToDisable, newState)
        }
    }

    override fun suspendInteropIdentity(
        holdingIdentityShortHash: String,
        interopIdentityShortHash: String
    ): ResponseEntity<String> {
        updateInteropIdentityEnablement(holdingIdentityShortHash, interopIdentityShortHash, false)
        return ResponseEntity(ResponseCode.OK, "OK")
    }

    override fun enableInteropIdentity(
        holdingIdentityShortHash: String,
        interopIdentityShortHash: String
    ): ResponseEntity<String> {
        updateInteropIdentityEnablement(holdingIdentityShortHash, interopIdentityShortHash, true)
        return ResponseEntity(ResponseCode.OK, "OK")
    }

    override fun deleteInteropIdentity(
        holdingIdentityShortHash: String,
        interopIdentityShortHash: String
    ): ResponseEntity<String> {
        val validHoldingIdentityShortHash = validateShortHash(holdingIdentityShortHash)
        val validInteropIdentityShortHash = validateShortHash(interopIdentityShortHash)

        val vNodeInfo = getAndValidateVirtualNodeInfoByShortHash(validHoldingIdentityShortHash)
        val vNodeShortHash = vNodeInfo.getVNodeShortHash()

        val registryView = interopIdentityRegistryService.getVirtualNodeRegistryView(vNodeShortHash)
        val identityToRemove = registryView.getIdentityWithShortHash(validInteropIdentityShortHash) ?:
            throw InvalidInputDataException(
                "No interop identity with short hash '$validInteropIdentityShortHash' found for holding " +
                        "identity '$validHoldingIdentityShortHash'."
            )

        if (identityToRemove.enabled) {
            throw InvalidInputDataException(
                "Interop identity '$interopIdentityShortHash' must be disabled prior to deletion. " +
                "Note: Deleting an interop identity will disrupt any active flow sessions which are using that identity. " +
                "Ensure that the identity is not in use attempting to delete it."
            )
        }

        interopIdentityWriteService.removeInteropIdentity(vNodeShortHash, identityToRemove)

        return ResponseEntity(ResponseCode.OK, "OK")
    }

    private fun facadeIds() = listOf(
        FacadeId.of("org.corda.interop/platform/tokens/v1.0"),
        FacadeId.of("org.corda.interop/platform/tokens/v2.0"),
        FacadeId.of("org.corda.interop/platform/tokens/v3.0")
    )

    override fun getInterOpIdentities(holdingIdentityShortHash: String): List<InteropIdentityResponse> {
        val validatedShortHash = validateShortHash(holdingIdentityShortHash)
        val vNodeInfo = getAndValidateVirtualNodeInfoByShortHash(validatedShortHash)
        val vNodeShortHash = vNodeInfo.getVNodeShortHash()
        val cacheView = interopIdentityRegistryService.getVirtualNodeRegistryView(vNodeShortHash)
        val interopIdentities = cacheView.getIdentities()
        return interopIdentities.map { interopIdentity ->
            InteropIdentityResponse(
                interopIdentity.x500Name,
                UUID.fromString(interopIdentity.groupId),
                interopIdentity.owningVirtualNodeShortHash.toString(),
                interopIdentity.facadeIds,
                MemberX500Name.parse(interopIdentity.x500Name).organization,
                interopIdentity.endpointUrl,
                interopIdentity.endpointProtocol,
                interopIdentity.enabled
            )
        }.toList()
    }

    override fun exportInterOpIdentity(
        holdingIdentityShortHash: String,
        interopIdentityShortHash: String
    ): ExportInteropIdentityRest.Response {
        val validHoldingIdentityShortHash = validateShortHash(holdingIdentityShortHash)
        val validInteropIdentityShortHash = validateShortHash(interopIdentityShortHash)
        val vNodeInfo = getAndValidateVirtualNodeInfoByShortHash(validHoldingIdentityShortHash)
        val vNodeShortHash = vNodeInfo.getVNodeShortHash()
        val registryView = interopIdentityRegistryService.getVirtualNodeRegistryView(vNodeShortHash)
        val interopIdentityToExport = registryView.getIdentityWithShortHash(validInteropIdentityShortHash)
            ?: throw InvalidInputDataException(
                "No interop identity with short hash '$validInteropIdentityShortHash' found for holding " +
                        "identity '$validHoldingIdentityShortHash'."
            )
        if (interopIdentityToExport.owningVirtualNodeShortHash != vNodeShortHash) {
            throw InvalidInputDataException(
                "Only owned identities may be exported." +
                        "Interop identity ${interopIdentityToExport.owningVirtualNodeShortHash}" +
                        "& yours is $vNodeShortHash"
            )
        }
        if (!interopIdentityToExport.enabled) {
            throw InvalidInputDataException("Cannot export a suspended identity.")
        }
        val groupPolicy = checkNotNull(interopGroupPolicyReadService.getGroupPolicy(interopIdentityToExport.groupId)) {
            "Could not find group policy info for interop identity $validInteropIdentityShortHash"
        }
        val node = mapper.readTree(groupPolicy)
        logger.info("The contents of the json node are: $node")
        check(node.has("p2pParameters")) { "Field 'p2pParameters' missing from response." }
        val p2pParameters = node.get("p2pParameters")
        check(p2pParameters.has("tlsTrustRoots")) { "Field 'tlsTrustRoots' missing from response." }
        val tlsTrustRoot = p2pParameters.get("tlsTrustRoots") as ArrayNode
        check(tlsTrustRoot.isArray) { "Expected '$tlsTrustRoot' to be an array." }
        val tlsCerts = tlsTrustRoot.map { it.textValue() }

        return ExportInteropIdentityRest.Response(
            listOf(
                ExportInteropIdentityRest.MemberData(
                    interopIdentityToExport.x500Name,
                    interopIdentityToExport.owningVirtualNodeShortHash.toString(),
                    interopIdentityToExport.endpointUrl,
                    interopIdentityToExport.endpointProtocol,
                    interopIdentityToExport.facadeIds.map { it.toString() }
                )
            ),

            GroupPolicy(
                node.get("fileFormatVersion").asInt(),
                node.get("groupId").asText(),
                P2pParameters(
                    p2pParameters.findValuesAsText("sessionTrustRoot"),
                    tlsCerts,
                    p2pParameters.get("sessionPki").asText(),
                    p2pParameters.get("tlsPki").asText(),
                    p2pParameters.get("tlsVersion").asText(),
                    p2pParameters.get("protocolMode").asText(),
                    p2pParameters.get("tlsType").asText()
                )
            )
        )
    }

    override fun importInterOpIdentity(
        importInteropIdentityRestRequest: ImportInteropIdentityRest.Request,
        holdingIdentityShortHash: String
    ): ResponseEntity<String> {
        val validHoldingIdentityShortHash = validateShortHash(holdingIdentityShortHash)

        if (importInteropIdentityRestRequest.members.isEmpty()) {
            throw InvalidInputDataException(
                "No members provided in request, nothing to import."
            )
        }

        val vNodeInfo = getAndValidateVirtualNodeInfoByShortHash(validHoldingIdentityShortHash)
        val vNodeShortHash = vNodeInfo.getVNodeShortHash()

        val json = mapper.writeValueAsString(importInteropIdentityRestRequest.groupPolicy)

        val interopGroupId = try {
            val groupIdField = getGroupIdFieldFromGroupPolicy(json)
            validateUUID(groupIdField) {
                "Malformed group policy, groupId is not a valid UUID string."
            }
            groupIdField
        } catch (e: Exception) {
            throw InvalidInputDataException(e.message!!)
        }

        interopIdentityWriteService.publishGroupPolicy(
            interopGroupId,
            json
        )

        importInteropIdentityRestRequest.members.forEach { member ->
            interopIdentityWriteService.addInteropIdentity(
                vNodeShortHash,
                InteropIdentity(
                    groupId = interopGroupId,
                    x500Name = member.x500Name,
                    facadeIds = member.facadeIds.map { FacadeId.of(it) },
                    owningVirtualNodeShortHash = ShortHash.of(member.owningIdentityShortHash),
                    applicationName = MemberX500Name.parse(member.x500Name).organization,
                    endpointUrl = member.endpointUrl,
                    endpointProtocol = member.endpointProtocol,
                    enabled = true
                )
            )
        }

        logger.info("Interop identity imported.")

        return ResponseEntity.ok("OK")
    }

    // Lifecycle
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::interopIdentityRegistryService,
        ::interopIdentityWriteService,
        ::interopGroupPolicyReadService,
        ::interopGroupPolicyValidator
    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<InteropRestResource>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> coordinator.updateStatus(LifecycleStatus.DOWN)
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.ERROR -> {
                        coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                        coordinator.postEvent(StopEvent(errored = true))
                    }

                    LifecycleStatus.UP -> {
                        // Receive updates to the REST and Messaging config
                        coordinator.createManagedResource(CONFIG_HANDLE) {
                            configurationReadService.registerComponentForUpdates(
                                coordinator,
                                requiredKeys
                            )
                        }
                    }

                    else -> logger.debug { "Unexpected status: ${event.status}" }
                }
                coordinator.updateStatus(event.status)
            }
        }
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()

}