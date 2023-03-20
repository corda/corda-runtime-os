package net.corda.chunking.db.impl.tests

import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.packaging.Cpi
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.util.Random
import java.util.UUID

fun newRandomSecureHash(): SecureHash {
    val random = Random()
    return SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
}

@Suppress("LongParameterList")
fun CpiPersistence.updateMetadataAndCpksWithDefaults(
    cpi: Cpi,
    cpiFileName: String = "test.cpi",
    cpiFileChecksum: SecureHash = newRandomSecureHash(),
    requestId: RequestId = UUID.randomUUID().toString(),
    groupId: String = "group-a",
    cpkDbChangeLog: List<CpkDbChangeLog> = emptyList(),
): CpiMetadataEntity = updateMetadataAndCpks(cpi, cpiFileName, cpiFileChecksum, requestId, groupId, cpkDbChangeLog)

@Suppress("LongParameterList")
fun CpiPersistence.persistMetadataAndCpksWithDefaults(
    cpi: Cpi,
    cpiFileName: String = "test.cpi",
    cpiFileChecksum: SecureHash = newRandomSecureHash(),
    requestId: RequestId = UUID.randomUUID().toString(),
    groupId: String = "group-a",
    cpkDbChangeLog: List<CpkDbChangeLog> = emptyList(),
): CpiMetadataEntity = persistMetadataAndCpks(cpi, cpiFileName, cpiFileChecksum, requestId, groupId, cpkDbChangeLog)
