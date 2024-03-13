package net.corda.gradle.plugin.dtos

// A collection of dataclasses representing objects used by the plugin

data class VNode(
    var x500Name: String? = null,
    var cpi: String? = null,
    var serviceX500Name : String? = null,
    var flowProtocolName: String? = null,
    var backchainRequired: String? = null,
)

data class GroupPolicyDTO(
    // Note, we only need protocolParameters rather than the full set of properties.
    var protocolParameters: ProtocolParametersDTO? = null
)

data class ProtocolParametersDTO(
    var sessionKeyPolicy: String? = null,
    var staticNetwork: StaticNetworkDTO? = null
)

data class StaticNetworkDTO(
    var members: List<MemberDTO>? = null
)

data class MemberDTO(
    var name: String? = null
)

data class CpiUploadResponseDTO(
    var id: String? = null
)

data class GetCPIsResponseDTO(
    var cpis: List<CpiMetadataDTO>? = null
)

data class CpiMetadataDTO(
    // Note, these DTOs don't cover all returned values, just the ones required for the plugin.
    var cpiFileChecksum: String? = null,
    var id: CpiIdentifierDTO? = null
)

data class CpiIdentifierDTO(
    // Note, these DTOs don't cover all returned values, just the ones required for the plugin
    var cpiName: String? = null,
    var cpiVersion: String? = null
)

data class CpiUploadStatus(
    var cpiFileChecksum: String? = null,
    var status: String? = null
)

data class VirtualNodeInfoDTO(
    // Note, these DTOs don't cover all returned values, just the ones required for the plugin.
    var holdingIdentity: HoldingIdentityDTO? = null,
    var cpiIdentifier: CpiIdentifierDTO? = null
)

data class VirtualNodesDTO(
    var virtualNodes: List<VirtualNodeInfoDTO>? = null
)

data class HoldingIdentityDTO(
    // Note, these DTOs don't cover all returned values, just the ones required for the plugin.
    var fullHash: String? = null,
    var groupId: String? = null,
    var shortHash: String? = null,
    var x500Name: String? = null
)

data class RegistrationRequestProgressDTO(
    // Note, these DTOs don't cover all returned values, just the ones required for the plugin.
    var registrationStatus: String? = null
)