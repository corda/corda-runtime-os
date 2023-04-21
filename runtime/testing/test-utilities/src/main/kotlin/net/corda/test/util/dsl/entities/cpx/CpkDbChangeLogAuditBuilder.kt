package net.corda.test.util.dsl.entities.cpx

import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAudit
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.v5.crypto.SecureHash
import java.util.UUID

fun cpkDbChangeLogAudit(init: CpkDbChangeLogAuditBuilder.() -> Unit): CpkDbChangeLogAudit {
    val builder = CpkDbChangeLogAuditBuilder()
    init(builder)
    return builder.build()
}

class CpkDbChangeLogAuditBuilder(
    private var fileChecksumSupplier: () -> SecureHash? = { null },
    private val randomUUID: UUID = UUID.randomUUID()
) {

    private var id: String? = null
    private var filePath: String? = null
    private var content: String? = null
    private var isDeleted: Boolean? = null

    fun id(value: String): CpkDbChangeLogAuditBuilder {
        id = value
        return this
    }

    fun fileChecksum(value: SecureHash): CpkDbChangeLogAuditBuilder {
        fileChecksumSupplier = { value }
        return this
    }

    fun filePath(value: String): CpkDbChangeLogAuditBuilder {
        filePath = value
        return this
    }

    fun content(value: String): CpkDbChangeLogAuditBuilder {
        content = value
        return this
    }

    fun build(): CpkDbChangeLogAudit {
        return CpkDbChangeLogAudit(
            id ?: "id_$randomUUID",
            CpkDbChangeLog(
                CpkDbChangeLogIdentifier(
                    fileChecksumSupplier.invoke() ?: SecureHashImpl(
                        "SHA-256",
                        "file_checksum_$randomUUID".toByteArray()),
                    filePath ?: "file_path_$randomUUID"
                ),
                content ?: "data_$randomUUID"
            )
        )
    }
}
