package net.corda.gradle.plugin.dtos

class HoldingIdentityDTO {
    // Note, these DTOs don't cover all returned values, just the ones required for the plugin.
    var fullHash: String? = null
    var groupId: String? = null
    var shortHash: String? = null
    var x500Name: String? = null
}
