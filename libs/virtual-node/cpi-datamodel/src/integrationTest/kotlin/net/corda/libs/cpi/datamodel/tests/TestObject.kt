package net.corda.libs.cpi.datamodel.tests

import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpkKey
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import java.util.UUID
import net.corda.libs.cpi.datamodel.CpiCpkEntity
import net.corda.libs.cpi.datamodel.CpiCpkKey

object TestObject {
    fun randomChecksumString(): String {
        return "SHA-256:" + List(64) {
            (('a'..'z') + ('A'..'Z') + ('0'..'9')).random()
        }.joinToString("")
    }

    fun createCpi(id: String, cpiName: String, cpiVersion: String, cpiSSH: String, cpks: Set<CpiCpkEntity>) =
        CpiMetadataEntity.create(
            cpiName, cpiVersion, cpiSSH,
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
    ) = CpkMetadataEntity(
        CpkKey(name, version, signerSummaryHash),
        cpkFileChecksum,
        "1.0",
        "{}"
    )

    fun createCpiCpkEntity(
        cpiName: String = "test-cpi-${UUID.randomUUID()}.cpk", cpiVersion: String, cpiSSH: String,
        cpkName: String, cpkVersion: String, cpkSSH: String,
        cpkFileName: String, cpkFileChecksum: String
    ) = CpiCpkEntity(
        CpiCpkKey(cpiName, cpiVersion, cpiSSH, cpkName, cpkVersion, cpkSSH),
        cpkFileName,
        cpkFileChecksum,
        createCpk(cpkFileChecksum, cpkName, cpkVersion, cpkSSH)
    )

    fun createCpiWithCpk(): Pair<CpiMetadataEntity, CpiCpkEntity> {
        val id = UUID.randomUUID().toString()
        val cpiName = "test-cpi-$id"
        val cpkFileChecksum = randomChecksumString()
        val cpkId = "test-cpk-$id.cpk"
        val cpiVersion = "1.0"
        val cpiSSH = "test-cpi-hash"
        val cpiCpkEntity = createCpiCpkEntity(
            cpiName, cpiVersion, cpiSSH,
            cpkId, "1.0",  "test-cpk-hash",
            "test-cpi-$id.cpk", cpkFileChecksum
        )
        val cpi = createCpi(id, cpiName, cpiVersion, cpiSSH, setOf(cpiCpkEntity))
        return Pair(cpi, cpiCpkEntity)
    }
}