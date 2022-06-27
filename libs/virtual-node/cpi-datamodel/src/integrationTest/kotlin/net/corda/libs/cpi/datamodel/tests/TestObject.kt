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

    fun createCpi(cpiId: UUID, cpks: List<Pair<String, CpiCpkEntity>>) =
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
        val cpiId = UUID.randomUUID()
        val cpkFileChecksum = randomChecksumString()
        val cpkId = "test-cpk-$cpiId.cpk"
        val ssh = "test-cpk=hash"
        val cpiCpkEntity = createCpiCpkEntity(
            "test-cpi-$cpiId", "1.0", ssh,
            cpkId, "1.0",  ssh,
            "test-cpi-$cpiId.cpi", cpkFileChecksum
        )
        val cpi = createCpi(
            cpiId,
            listOf(
                Pair("test-cpk-$cpiId.cpk", cpiCpkEntity)
            )
        )
        return Pair(cpi, cpiCpkEntity)
    }
}