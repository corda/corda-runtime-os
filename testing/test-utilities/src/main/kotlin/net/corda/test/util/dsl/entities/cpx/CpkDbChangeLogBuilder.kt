package net.corda.test.util.dsl.entities.cpx

import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.v5.crypto.SecureHash
import java.util.UUID

fun cpkDbChangeLog(init: CpkDbChangeLogBuilder.() -> Unit): CpkDbChangeLog {
    val builder = CpkDbChangeLogBuilder()
    init(builder)
    return builder.build()
}

class CpkDbChangeLogBuilder(
    private var fileChecksumSupplier: () -> SecureHash? = { null },
    private val randomUUID: UUID = UUID.randomUUID()
) {

    private var filePath: String? = null

    fun fileChecksum(value: SecureHash): CpkDbChangeLogBuilder {
        fileChecksumSupplier = { value }
        return this
    }

    fun filePath(value: String): CpkDbChangeLogBuilder {
        filePath = value
        return this
    }

    fun build(): CpkDbChangeLog {
        return CpkDbChangeLog(
            CpkDbChangeLogIdentifier(
                fileChecksumSupplier.invoke() ?: SecureHashImpl("SHA-256", "file_checksum_$randomUUID".toByteArray()),
                filePath ?: "file_path_$randomUUID"
            ),
            "data_$randomUUID"
        )
    }
}
