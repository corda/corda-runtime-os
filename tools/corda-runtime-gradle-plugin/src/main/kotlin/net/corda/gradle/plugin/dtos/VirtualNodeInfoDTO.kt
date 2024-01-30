package net.corda.gradle.plugin.dtos

class VirtualNodeInfoDTO {
    // Note, these DTOs don't cover all returned values, just the ones required for the plugin.
    var holdingIdentity: HoldingIdentityDTO? = null
    var cpiIdentifier: CpiIdentifierDTO? = null
}