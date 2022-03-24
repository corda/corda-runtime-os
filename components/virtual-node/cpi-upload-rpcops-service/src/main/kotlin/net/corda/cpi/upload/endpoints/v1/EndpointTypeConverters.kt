package net.corda.cpi.upload.endpoints.v1

import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.cpiupload.endpoints.v1.CpiMetadata
import net.corda.libs.cpiupload.endpoints.v1.CpkIdentifier
import net.corda.libs.cpiupload.endpoints.v1.CpkMetadata

internal fun net.corda.libs.packaging.CpiIdentifier.toEndpointType() =
    CpiIdentifier(this.name, this.version, this.signerSummaryHash.toString())

internal fun net.corda.libs.packaging.CpiMetadata.toEndpointType() =
    CpiMetadata(
        this.cpiId.toEndpointType(),
        this.fileChecksum.toHexString(),
        this.cpksMetadata.map { cpkMetadata -> cpkMetadata.toEndpointType() },
        this.groupPolicy
    )

internal fun net.corda.libs.packaging.CpkIdentifier.toEndpointType() =
    CpkIdentifier(this.name, this.version, this.signerSummaryHash.toString())

internal fun net.corda.libs.packaging.CpkMetadata.toEndpointType() =
    CpkMetadata(
        this.cpkId.toEndpointType(),
        this.mainBundle,
        this.libraries,
        this.dependencies.map { cpkIdentifier -> cpkIdentifier.toEndpointType() },
        this.type.toString(),
        this.fileChecksum.toString()
    )