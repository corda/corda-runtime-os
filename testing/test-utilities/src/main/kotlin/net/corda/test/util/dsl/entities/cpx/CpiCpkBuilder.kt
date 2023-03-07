package net.corda.test.util.dsl.entities.cpx

import java.util.UUID
import net.corda.libs.cpi.datamodel.entities.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.CpiCpkKey
import net.corda.libs.cpi.datamodel.entities.CpkMetadataEntity
import net.corda.v5.crypto.SecureHash

class CpiCpkBuilder(
    private var cpkFileChecksumSupplier: () -> SecureHash? = { null },
    private var cpiNameSupplier: () -> String? = { null },
    private var cpiVersionSupplier: () -> String? = { null },
    private var cpiSignerSummaryHashSupplier: () -> SecureHash? = { null },
    private var randomId: UUID = UUID.randomUUID()
) {

    constructor(
        cpk: CpkMetadataBuilder,
        cpiNameSupplier: () -> String? = { null },
        cpiVersionSupplier: () -> String? = { null },
        cpiSignerSummaryHashSupplier: () -> SecureHash? = { null },
    ) : this(cpk.fileChecksumSupplier, cpiNameSupplier, cpiVersionSupplier, cpiSignerSummaryHashSupplier) {
        cpkName = cpk.cpkName
        cpkVersion = cpk.cpkVersion
        cpkSignerSummaryHash = cpk.cpkSignerSummaryHash
        formatVersion = cpk.formatVersion
        serializedMetadata = cpk.serializedMetadata
        metadata = cpk
    }

    constructor(
        cpk: CpkMetadataEntity,
        cpiNameSupplier: () -> String? = { null },
        cpiVersionSupplier: () -> String? = { null },
        cpiSignerSummaryHashSupplier: () -> SecureHash? = { null },
    ) : this(
        { SecureHash.parse(cpk.cpkFileChecksum) },
        cpiNameSupplier,
        cpiVersionSupplier,
        cpiSignerSummaryHashSupplier
    ) {
        cpkName = cpk.cpkName
        cpkVersion = cpk.cpkVersion
        cpkSignerSummaryHash = SecureHash.parse(cpk.cpkSignerSummaryHash)
        formatVersion = cpk.formatVersion
        serializedMetadata = cpk.serializedMetadata
        metadataEntity = cpk
    }

    // cpk
    private var cpkName: String? = null
    private var cpkVersion: String? = null
    private var cpkSignerSummaryHash: SecureHash? = null

    // cpicpk
    private var fileName: String? = null
    private var metadata: CpkMetadataBuilder? = null

    //metadata
    private var metadataEntity: CpkMetadataEntity? = null
    private var formatVersion: String? = null
    private var serializedMetadata: String? = null

    fun instanceId(value: UUID): CpiCpkBuilder {
        randomId = value
        return this
    }

    fun cpiName(value: String): CpiCpkBuilder {
        cpiNameSupplier = { value }
        return this
    }

    fun cpiVersion(value: String): CpiCpkBuilder {
        cpiVersionSupplier = { value }
        return this
    }

    fun cpiSignerSummaryHash(value: SecureHash): CpiCpkBuilder {
        cpiSignerSummaryHashSupplier = { value }
        return this
    }

    fun cpkName(value: String): CpiCpkBuilder {
        cpkName = value
        return this
    }

    fun cpkVersion(value: String): CpiCpkBuilder {
        cpkVersion = value
        return this
    }

    fun cpkSignerSummaryHash(value: SecureHash): CpiCpkBuilder {
        cpkSignerSummaryHash = value
        return this
    }

    fun fileName(value: String): CpiCpkBuilder {
        fileName = value
        return this
    }

    fun metadata(init: CpkMetadataBuilder.() -> Unit): CpiCpkBuilder {
        val cpkMetadata = CpkMetadataBuilder(cpkFileChecksumSupplier, randomId)
        init(cpkMetadata)
        metadata = cpkMetadata
        return this
    }

    fun metadata(value: CpkMetadataBuilder): CpiCpkBuilder {
        metadata = value
        return this
    }

    fun fileChecksum(value: SecureHash): CpiCpkBuilder {
        cpkFileChecksumSupplier = { value }
        return this
    }

    fun serializedMetadata(value: String): CpiCpkBuilder {
        serializedMetadata = value
        return this
    }

    @Suppress("ThrowsCount")
    fun build(): CpiCpkEntity {
        if (cpkFileChecksumSupplier.invoke() == null) cpkFileChecksumSupplier =
            { SecureHash("SHA-256", "cpk_file_checksum_$randomId".toByteArray()) }
        val cpk: CpkMetadataEntity = metadataEntity
            ?: metadata?.build() ?: CpkMetadataBuilder(cpkFileChecksumSupplier, randomId)
                .cpkName(cpkName ?: "cpkName_$randomId")
                .cpkVersion(cpkVersion ?: "cpkVersion_$randomId")
                .cpkSignerSummaryHash(
                    cpkSignerSummaryHash ?: SecureHash(
                        "SHA-256",
                        "cpkSignerSummaryHash_$randomId".toByteArray()
                    )
                )
                .formatVersion(formatVersion)
                .serializedMetadata(serializedMetadata)
                .build()

        return CpiCpkEntity(
            CpiCpkKey(
                cpiNameSupplier.invoke() ?: throw DslException("CpiCpkBuilder.cpiNameSupplier is mandatory"),
                cpiVersionSupplier.invoke() ?: throw DslException("CpiCpkBuilder.cpiVersionSupplier is mandatory"),
                cpiSignerSummaryHashSupplier.invoke().toString(),
                cpkFileChecksumSupplier()!!.toString()
            ),
            fileName ?: "cpk_filename_$randomId",
            cpk
        )
    }
}