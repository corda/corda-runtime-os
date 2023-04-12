package net.corda.test.util.dsl.entities.cpx

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo

fun CpiInfoReadService.getCpkFileHashes(virtualNodeInfo: VirtualNodeInfo): MutableSet<SecureHash> {
    val cpkMetadata = this.get(virtualNodeInfo.cpiIdentifier)?.cpksMetadata!!
    return cpkMetadata.mapTo(mutableSetOf(), CpkMetadata::fileChecksum)
}
