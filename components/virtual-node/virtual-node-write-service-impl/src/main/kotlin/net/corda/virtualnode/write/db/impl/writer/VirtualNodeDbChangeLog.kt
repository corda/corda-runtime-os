package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.admin.DbChange
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.io.InputStream

/**
 * DB Change Log for all CPKs for a Virtual Node, as used by Liquibase during VNode creation.
 *
 * @property changeLogs list of all [CpkDbChangeLogEntity] for all CPKs to be DB migrated.
 * @constructor Create empty Virtual node db change log
 */
class VirtualNodeDbChangeLog(
    private val changeLogs: List<CpkDbChangeLogEntity>
): DbChange {
    companion object {
        const val MASTER_CHANGE_LOG = "db.changelog-master.xml"
    }

    private val all by lazy {
        changeLogs.associate {
            "${it.id.cpkName}-${it.id.filePath}" to it.content
        }
    }

    override val masterChangeLogFiles: List<String>
        get() = changeLogs
            // TODO - ensure validation that a CPK with changelog files has at least 1 master file
            //  must happen during CPI installation.
            .filter {
                it.id.filePath == MASTER_CHANGE_LOG
            }.map {
                "${it.id.cpkName}-${it.id.filePath}"
            }

    override val changeLogFileList: Set<String>
        get() = all.keys

    override fun fetch(path: String): InputStream {
        return all[path]?.byteInputStream()
            ?: throw CordaRuntimeException("Cannot find changelog file: $path")
    }
}