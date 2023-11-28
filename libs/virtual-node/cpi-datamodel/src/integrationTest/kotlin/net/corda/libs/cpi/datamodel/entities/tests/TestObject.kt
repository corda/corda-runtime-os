package net.corda.libs.cpi.datamodel.entities.tests

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.parseSecureHash
import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.libs.cpi.datamodel.entities.internal.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpiCpkKey
import net.corda.libs.cpi.datamodel.entities.internal.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpkMetadataEntity
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

internal object TestObject {

    val SIGNER_SUMMARY_HASH = SecureHashImpl("SHA-256", "test-cpi-hash".toByteArray())

    private fun genRandomChecksumString(): String {
        return "SHA-256:" + List(64) {
            (('a'..'f') + ('A'..'F') + ('0'..'9')).random()
        }.joinToString("")
    }

    fun genRandomChecksum(): SecureHash {
        return parseSecureHash(genRandomChecksumString())
    }

    fun createCpi(id: String, cpiName: String, cpiVersion: String, cpiSignerSummaryHash: SecureHash, cpks: Set<CpiCpkEntity>) =
        CpiMetadataEntity.create(
            CpiIdentifier(cpiName, cpiVersion, cpiSignerSummaryHash),
            "test-cpi-$id.cpi",
            SecureHashImpl("SHA-256", "test-cpi.cpi-$id-hash".toByteArray()),
            "{group-policy-json}",
            "group-id",
            "file-upload-request-id-$id",
            cpks
        )

    fun createCpi(cpiId: UUID, cpks: Set<CpiCpkEntity>) =
        CpiMetadataEntity.create(
            CpiIdentifier("test-cpi-$cpiId", "1.0", SIGNER_SUMMARY_HASH),
            "test-cpi-$cpiId.cpi",
            SecureHashImpl("SHA-256", "test-cpi.cpi-$cpiId-hash".toByteArray()),
            "{group-policy-json}",
            "group-id",
            "file-upload-request-id-$cpiId",
            cpks
        )

    fun createCpk(
        cpkFileChecksum: String,
        name: String,
        version: String,
        signerSummaryHash: String,
    ) = CpkMetadataEntity(
        cpkFileChecksum,
        name,
        version,
        signerSummaryHash,
        "1.0",
        CpkMetadata(
            CpkIdentifier(
                name,
                version,
                parseSecureHash(signerSummaryHash)
            ),
            CpkManifest(CpkFormatVersion(2, 3)),
            "mainBundle.jar",
            listOf("library.jar"),
            CordappManifest(
                "com.r3.corda.Bundle",
                "1.2.3",
                12,
                34,
                CordappType.WORKFLOW,
                "someName",
                "R3",
                42,
                "some license",
                mapOf(
                    "Corda-Contract-Classes" to "contractClass1, contractClass2",
                    "Corda-Flow-Classes" to "flowClass1, flowClass2"
                ),
            ),
            CpkType.CORDA_API,
            SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32)),
            emptySet(),
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            externalChannelsConfig = "{}"
        ).toJsonAvro()
    )

    fun genRandomCpkFile() =
        createCpkFile(SecureHashImpl("SHA-256", "cpk-checksum-${UUID.randomUUID()}".toByteArray()), ByteArray(2000))

    fun createCpkFile(
        fileChecksum: SecureHash,
        data: ByteArray
    ) = CpkFile(fileChecksum, data, 0)

    @Suppress("LongParameterList")
    fun createCpiCpkEntity(
        cpiName: String = "test-cpi-${UUID.randomUUID()}.cpk",
        cpiVersion: String,
        cpiSignerSummaryHash: SecureHash,
        cpkName: String,
        cpkVersion: String,
        cpkSignerSummaryHash: String,
        cpkFileName: String,
        cpkFileChecksum: String
    ) = CpiCpkEntity(
        CpiCpkKey(cpiName, cpiVersion, cpiSignerSummaryHash.toString(), cpkFileChecksum),
        cpkFileName,
        createCpk(cpkFileChecksum, cpkName, cpkVersion, cpkSignerSummaryHash)
    )

    fun createCpiWithCpks(numberOfCpks: Int = 1): Pair<CpiMetadataEntity, List<CpiCpkEntity>> {
        val id = UUID.randomUUID().toString()
        val cpiName = "test-cpi-$id"
        val cpiVersion = "1.0"
        val cpkList: List<CpiCpkEntity> = (1..numberOfCpks).map {
            val cpkFileChecksum = genRandomChecksum()
            val cpkName = UUID.randomUUID().toString()
            val cpkId = "test-cpk-$cpkName.cpk"
            createCpiCpkEntity(
                cpiName,
                cpiVersion,
                SIGNER_SUMMARY_HASH,
                cpkId,
                "1.0",
                SecureHashImpl("SHA-256", "test-cpk-hash".toByteArray()).toString(),
                "test-cpi-$id.cpk",
                cpkFileChecksum.toString()
            )
        }
        val cpi = createCpi(id, cpiName, cpiVersion, SIGNER_SUMMARY_HASH, cpkList.toSet())

        return Pair(cpi, cpkList)
    }
}
