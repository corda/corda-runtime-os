package net.corda.test.util.dsl.entities.cpx

import java.util.UUID
import net.corda.libs.cpi.datamodel.entities.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.entities.CpkDbChangeLogKey

fun cpkDbChangeLog(init: CpkDbChangeLogBuilder.() -> Unit): CpkDbChangeLogEntity {
    val builder = CpkDbChangeLogBuilder()
    init(builder)
    return builder.build()
}

class CpkDbChangeLogBuilder(private var fileChecksumSupplier: () -> String? = { null }, private val randomUUID: UUID = UUID.randomUUID()) {

    private var filePath: String? = null

    fun fileChecksum(value: String): CpkDbChangeLogBuilder {
        fileChecksumSupplier = { value }
        return this
    }

    fun filePath(value: String): CpkDbChangeLogBuilder {
        filePath = value
        return this
    }

    fun build(): CpkDbChangeLogEntity {
        return CpkDbChangeLogEntity(
            CpkDbChangeLogKey(
                fileChecksumSupplier.invoke() ?: "file_checksum_$randomUUID",
                filePath ?: "file_path_$randomUUID"
            ),
            "data_$randomUUID"
        )
    }
}