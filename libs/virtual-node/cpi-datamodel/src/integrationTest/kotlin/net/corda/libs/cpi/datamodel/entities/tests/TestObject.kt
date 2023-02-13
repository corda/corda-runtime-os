package net.corda.libs.cpi.datamodel.entities.tests

import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.entities.CpkMetadataEntity
import java.util.UUID
import net.corda.libs.cpi.datamodel.entities.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.CpiCpkKey
import net.corda.v5.crypto.SecureHash

object TestObject {
    fun randomChecksumString(): String {
        return "SHA-256:" + List(64) {
            (('a'..'z') + ('A'..'Z') + ('0'..'9')).random()
        }.joinToString("")
    }

    fun createCpi(id: String, cpiName: String, cpiVersion: String, cpiSignerSummaryHash: String, cpks: Set<CpiCpkEntity>) =
        CpiMetadataEntity.create(
            cpiName, cpiVersion, cpiSignerSummaryHash,
            "test-cpi-$id.cpi",
            "test-cpi.cpi-$id-hash",
            "{group-policy-json}",
            "group-id",
            "file-upload-request-id-$id",
            cpks
        )

    fun createCpi(cpiId: UUID, cpks: Set<CpiCpkEntity>) =
        CpiMetadataEntity.create(
            "test-cpi-$cpiId",
            "1.0",
            "test-cpi-hash",
            "test-cpi-$cpiId.cpi",
            "test-cpi.cpi-$cpiId-hash",
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
    ) = CpkMetadataEntity(cpkFileChecksum, name, version, signerSummaryHash, "1.0", "{}")

    fun createCpiCpkEntity(
        cpiName: String = "test-cpi-${UUID.randomUUID()}.cpk", cpiVersion: String, cpiSignerSummaryHash: String,
        cpkName: String, cpkVersion: String, cpkSignerSummaryHash: String,
        cpkFileName: String, cpkFileChecksum: String
    ) = CpiCpkEntity(
        CpiCpkKey(cpiName, cpiVersion, cpiSignerSummaryHash, cpkFileChecksum),
        cpkFileName,
        createCpk(cpkFileChecksum, cpkName, cpkVersion, cpkSignerSummaryHash)
    )

    fun createCpiWithCpks(numberOfCpks: Int = 1): Pair<CpiMetadataEntity, List<CpiCpkEntity>> {
        val id = UUID.randomUUID().toString()
        val cpiName = "test-cpi-$id"
        val cpiVersion = "1.0"
        val cpiSignerSummaryHash = SecureHash("SHA1","test-cpi-hash".toByteArray()).toString()
        val cpkList: List<CpiCpkEntity> = (1..numberOfCpks).map {
            val cpkFileChecksum = randomChecksumString()
            val cpkName = UUID.randomUUID().toString()
            val cpkId = "test-cpk-$cpkName.cpk"
            createCpiCpkEntity(
                cpiName, cpiVersion, cpiSignerSummaryHash,
                cpkId, "1.0", SecureHash("SHA1", "test-cpk-hash".toByteArray()).toString(),
                "test-cpi-$id.cpk", cpkFileChecksum
            )
        }
        val cpi = createCpi(id, cpiName, cpiVersion, cpiSignerSummaryHash, cpkList.toSet())

        return Pair(cpi, cpkList)
    }


}