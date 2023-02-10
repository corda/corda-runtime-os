package net.corda.chunking.db.impl.tests

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.entities.CpkDbChangeLogEntity
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.util.Random
import java.util.UUID

val Cpk.fileChecksum: String get() = metadata.fileChecksum.toString()


fun newRandomSecureHash(): SecureHash {
    val random = Random()
    return SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
}

fun CpiPersistence.updateMetadataAndCpksWithDefaults(
    cpi: Cpi,
    cpiFileName: String = "test.cpi",
    cpiFileChecksum: SecureHash = newRandomSecureHash(),
    requestId: RequestId = UUID.randomUUID().toString(),
    groupId: String = "group-a",
    cpkDbChangeLog: List<CpkDbChangeLog> = emptyList(),
): CpiMetadataEntity = updateMetadataAndCpks(cpi, cpiFileName, cpiFileChecksum, requestId, groupId, cpkDbChangeLog)

fun CpiPersistence.persistMetadataAndCpksWithDefaults(
    cpi: Cpi,
    cpiFileName: String = "test.cpi",
    cpiFileChecksum: SecureHash = newRandomSecureHash(),
    requestId: RequestId = UUID.randomUUID().toString(),
    groupId: String = "group-a",
    cpkDbChangeLog: List<CpkDbChangeLog> = emptyList(),
): CpiMetadataEntity = persistMetadataAndCpks(cpi, cpiFileName, cpiFileChecksum, requestId, groupId, cpkDbChangeLog)