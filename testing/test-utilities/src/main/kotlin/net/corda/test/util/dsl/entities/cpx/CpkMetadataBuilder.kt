package net.corda.test.util.dsl.entities.cpx

import java.util.UUID
import net.corda.libs.cpi.datamodel.entities.CpkMetadataEntity
import net.corda.v5.crypto.SecureHash

fun cpk(init: CpkMetadataBuilder.() -> Unit): CpkMetadataEntity {
    val cpkBuilder = CpkMetadataBuilder()
    init(cpkBuilder)
    return cpkBuilder.build()
}

class CpkMetadataBuilder(
    internal var fileChecksumSupplier: () -> SecureHash? = { null },
    private var randomId: UUID = UUID.randomUUID()
) {

    internal var formatVersion: String? = null
    internal var serializedMetadata: String? = null
    internal var cpkName: String? = null
    internal var cpkVersion: String? = null
    internal var cpkSignerSummaryHash: SecureHash? = null

    fun instanceId(value: UUID): CpkMetadataBuilder {
        randomId = value
        return this
    }

    fun fileChecksum(value: SecureHash?): CpkMetadataBuilder {
        fileChecksumSupplier = { value }
        return this
    }

    fun formatVersion(value: String?): CpkMetadataBuilder {
        formatVersion = value
        return this
    }

    fun serializedMetadata(value: String?): CpkMetadataBuilder {
        serializedMetadata = value
        return this
    }

    fun cpkName(value: String?): CpkMetadataBuilder {
        cpkName = value
        return this
    }

    fun cpkVersion(value: String?): CpkMetadataBuilder {
        cpkVersion = value
        return this
    }

    fun cpkSignerSummaryHash(value: SecureHash?): CpkMetadataBuilder {
        cpkSignerSummaryHash = value
        return this
    }

    fun build(): CpkMetadataEntity {
        return CpkMetadataEntity(
            (fileChecksumSupplier.invoke() ?: SecureHash(
                "SHA-256",
                "cpk_file_checksum_$randomId".toByteArray()
            )).toString(),
            cpkName ?: "name_$randomId",
            cpkVersion ?: "version_$randomId",
            (cpkSignerSummaryHash ?: SecureHash("SHA-256", "signerSummaryHash_$randomId".toByteArray())).toString(),
            formatVersion ?: "format_version_$randomId".take(12),
            serializedMetadata ?: "serialized_metadata_$randomId"
        )
    }
}
