package net.corda.chunking.db.impl.tests

import net.corda.chunking.RequestId
import net.corda.cpi.persistence.CpiPersistence
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.util.Random
import java.util.UUID

val Cpk.csum: String get() = metadata.fileChecksum.toString()

val random = Random(0)

fun newRandomSecureHash(): SecureHash {
    return SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
}

fun CpiPersistence.storeWithTestDefaults(
    cpi: Cpi,
    cpiFileName: String = "test.cpi",
    checksum: SecureHash = newRandomSecureHash(),
    requestId: RequestId = UUID.randomUUID().toString(),
    groupId: String = "group-a",
    cpkDbChangeLogEntities: List<CpkDbChangeLogEntity> = emptyList(),
    forceCpiUpdate: Boolean = false
): CpiMetadataEntity =
    if (forceCpiUpdate) updateMetadataAndCpks(cpi, cpiFileName, checksum, requestId, groupId, cpkDbChangeLogEntities)
    else persistMetadataAndCpks(cpi, cpiFileName, checksum, requestId, groupId, cpkDbChangeLogEntities)
