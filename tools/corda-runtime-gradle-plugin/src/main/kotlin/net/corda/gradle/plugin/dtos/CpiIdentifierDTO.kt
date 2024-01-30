package net.corda.gradle.plugin.dtos

class CpiIdentifierDTO {
    // Note, these DTOs don't cover all returned values, just the ones required for the plugin.
    var cpiName: String? = null
    var cpiVersion: String? = null
    var signerSummaryHash: String? = null
}
