package net.corda.test.util.dsl.entities.cpx

import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import java.util.UUID

fun cpkDbChangeLog(init: CpkDbChangeLogBuilder.() -> Unit): CpkDbChangeLog {
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

    fun build(): CpkDbChangeLog {
        return CpkDbChangeLog(
            filePath ?: "file_path_$randomUUID",
            "data_$randomUUID",
            fileChecksumSupplier.invoke() ?: "file_checksum_$randomUUID"
        )
    }
}