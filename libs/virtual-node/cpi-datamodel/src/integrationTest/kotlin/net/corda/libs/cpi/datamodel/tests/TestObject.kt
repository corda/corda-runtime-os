package net.corda.libs.cpi.datamodel.tests

import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpkKey
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import java.util.UUID

object TestObject {
    fun randomChecksumString(): String {
        return "SHA-256:" + List(64) {
            (('a'..'z') + ('A'..'Z') + ('0'..'9')).random()
        }.joinToString("")
    }

    fun createCpi(
        cpiId: UUID,
        cpks: List<Pair<String, CpkMetadataEntity>>,
    ) =
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

    fun createCpiWithCpk(): Pair<CpiMetadataEntity, CpkMetadataEntity> {
        val cpiId = UUID.randomUUID()
        val cpk = createCpk(
            randomChecksumString(),
            "test-cpk-$cpiId",
            "1.0",
            "test-cpk=hash"
        )
        val cpi = createCpi(
            cpiId,
            listOf(
                Pair("test-cpk-$cpiId.cpk", cpk)
            )
        )
        return Pair(cpi, cpk)
    }
}