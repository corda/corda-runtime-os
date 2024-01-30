package net.corda.gradle.plugin.dtos

class CpiMetadataDTO {
    // Note, these DTOs don't cover all returned values, just the ones required for the plugin.
    var cpiFileChecksum: String? = null
    var id: CpiIdentifierDTO? = null
}
