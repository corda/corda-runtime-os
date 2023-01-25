package net.corda.test.util.dsl.entities.cpx

import java.util.UUID
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpkMetadataEntity

fun cpi(init: CpiBuilder.() -> Unit): CpiMetadataEntity {
    val cpi = CpiBuilder()
    init(cpi)
    return cpi.build()
}

class CpiBuilder(private val randomId: UUID = UUID.randomUUID()) {
    private var name: String? = null
    private var version: String? = null
    private var signerSummaryHash: String? = null
    private var fileName: String? = null
    private var groupPolicy: String? = null
    private var groupId: String? = null
    private var fileUploadRequestId: String? = null
    private var fileChecksum: String? = null
    private var cpks: MutableSet<CpiCpkBuilder> = mutableSetOf()
    private var entityVersion: Int? = null

    fun name(value: String): CpiBuilder {
        name = value
        return this
    }

    fun version(value: String): CpiBuilder {
        version = value
        return this
    }

    fun signerSummaryHash(value: String): CpiBuilder {
        signerSummaryHash = value
        return this
    }

    fun fileName(value: String): CpiBuilder {
        fileName = value
        return this
    }

    fun fileChecksum(value: String): CpiBuilder {
        fileChecksum = value
        return this
    }

    fun groupPolicy(value: String): CpiBuilder {
        groupPolicy = value
        return this
    }

    fun groupId(value: String): CpiBuilder {
        groupId = value
        return this
    }

    fun fileUploadRequestId(value: String): CpiBuilder {
        fileUploadRequestId = value
        return this
    }

    fun entityVersion(value: Int): CpiBuilder {
        entityVersion = value
        return this
    }

    fun cpk(init: CpiCpkBuilder.() -> Unit): CpiBuilder {
        val cpk = CpiCpkBuilder(
            cpiNameSupplier = ::supplyCpiName,
            cpiVersionSupplier = ::supplyCpiVersion,
            cpiSignerSummaryHashSupplier = ::supplyCpiSignerSummaryHash
        )
        init(cpk)
        cpks.add(cpk)
        return this
    }

    fun cpk(cpkMetadata: CpkMetadataEntity, additionalInit: (CpiCpkBuilder.() -> Unit)? = null): CpiBuilder {
        val cpk = CpiCpkBuilder(
            cpk = cpkMetadata,
            cpiNameSupplier = ::supplyCpiName,
            cpiVersionSupplier = ::supplyCpiVersion,
            cpiSignerSummaryHashSupplier = ::supplyCpiSignerSummaryHash,
        )
        additionalInit?.let { cpk.additionalInit() }
        cpks.add(cpk)
        return this
    }

    fun cpk(cpkMetadataBuilder: CpkMetadataBuilder, additionalInit: (CpiCpkBuilder.() -> Unit)? = null): CpiBuilder {
        val cpk = CpiCpkBuilder(
            cpk = cpkMetadataBuilder,
            cpiNameSupplier = ::supplyCpiName,
            cpiVersionSupplier = ::supplyCpiVersion,
            cpiSignerSummaryHashSupplier = ::supplyCpiSignerSummaryHash,
        )
        additionalInit?.let { cpk.additionalInit() }
        cpks.add(cpk)
        return this
    }

    private fun supplyCpiName() = name
    private fun supplyCpiVersion() = version
    private fun supplyCpiSignerSummaryHash() = signerSummaryHash

    @Suppress("ComplexMethod")
    fun build(): CpiMetadataEntity {
        val randomCpkId = "${randomId}_${UUID.randomUUID()}"
        if (name == null) name = "name_$randomCpkId"
        if (version == null) version = "version_$randomCpkId"
        if (signerSummaryHash == null) signerSummaryHash = "signerSummaryHash_$randomCpkId"
        if (fileChecksum == null) fileChecksum = "file_checksum_$randomCpkId"
        return CpiMetadataEntity(
            name!!,
            version!!,
            signerSummaryHash!!,
            fileName ?: "filename_$randomCpkId",
            fileChecksum!!,
            groupPolicy ?: "group_policy_$randomCpkId",
            groupId ?: "group_id_$randomCpkId",
            fileUploadRequestId ?: "upload_req_id_$randomCpkId",
            cpks.map { it.build() }.toSet(),
            entityVersion = entityVersion ?: 0
        )
    }
}
