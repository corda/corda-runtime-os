package net.corda.cpi.upload.endpoints.v1

import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.cpiupload.endpoints.v1.CpiMetadata
import net.corda.libs.cpiupload.endpoints.v1.CpkIdentifier
import net.corda.libs.cpiupload.endpoints.v1.CpkMetadata
import kotlin.math.min

internal fun net.corda.libs.packaging.core.CpiIdentifier.toEndpointType() =
    CpiIdentifier(this.name, this.version, this.signerSummaryHash.toString())

internal fun net.corda.libs.packaging.core.CpiMetadata.toEndpointType(): CpiMetadata {
    val hexString = this.fileChecksum.toHexString()
    return CpiMetadata(
        id = this.cpiId.toEndpointType(),
        cpiFileChecksum = hexString.substring(0, min(12, hexString.length)),
        cpiFileFullChecksum = hexString,
        cpks = this.cpksMetadata.map { cpkMetadata -> cpkMetadata.toEndpointType() },
        groupPolicy = this.groupPolicy,
        timestamp = this.timestamp
    )
}

internal fun net.corda.libs.packaging.core.CpkIdentifier.toEndpointType() =
    CpkIdentifier(this.name, this.version, this.signerSummaryHash.toString())

internal fun net.corda.libs.packaging.core.CpkMetadata.toEndpointType() =
    CpkMetadata(
        this.cpkId.toEndpointType(),
        this.mainBundle,
        this.libraries,
        this.type.toString(),
        this.fileChecksum.toString(),
        this.timestamp
    )
