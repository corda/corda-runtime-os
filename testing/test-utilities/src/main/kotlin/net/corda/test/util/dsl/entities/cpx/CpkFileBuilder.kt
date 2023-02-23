package net.corda.test.util.dsl.entities.cpx

import java.util.UUID
import net.corda.libs.cpi.datamodel.entities.CpkFileEntity

fun cpkFile(init: CpkFileBuilder.() -> Unit): CpkFileEntity {
    val cpkFileBuilder = CpkFileBuilder()
    init(cpkFileBuilder)
    return cpkFileBuilder.build()
}

class CpkFileBuilder(private var fileChecksumSupplier: () -> String? = { null }, private val randomUUID: UUID = UUID.randomUUID()) {

    fun fileChecksum(value: String): CpkFileBuilder {
        fileChecksumSupplier = { value }
        return this
    }

    fun build(): CpkFileEntity {
        return CpkFileEntity(
            fileChecksumSupplier.invoke() ?: "file_checksum_$randomUUID",
            "data_$randomUUID".toByteArray(),
        )
    }
}