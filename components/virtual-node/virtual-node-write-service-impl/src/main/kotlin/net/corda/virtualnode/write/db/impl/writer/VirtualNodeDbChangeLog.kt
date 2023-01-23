package net.corda.virtualnode.write.db.impl.writer

import net.corda.db.admin.DbChange
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.io.InputStream

/**
 * DB Change Log for all CPKs for a Virtual Node, as used by Liquibase during VNode creation.
 *
 * @property changeLogs list of all [CpkDbChangeLogEntity] for all CPKs to be DB migrated.
 * @constructor Create empty Virtual node db change log
 */
class VirtualNodeDbChangeLog(
    private val changeLogs: List<CpkDbChangeLog>,
) : DbChange {
    companion object {
        // To get going we assume the master changelog file for a CPK is XML and has this name
        // Note that this name different to the example in:
        //    https://docs.liquibase.com/concepts/bestpractices.html
        // And we know people use other names for master files, e.g. in the liquibase test cases at:
        //    https://github.com/liquibase/liquibase/tree/master/liquibase-integration-tests/src/test/resources/changelogs/pgsql/complete
        //
        // For command line usage the root changelog is a required parameter, see https://docs.liquibase.com/commands/update/update.html
        const val MASTER_CHANGE_LOG = "migration/db.changelog-master.xml"
    }


    private val all by lazy {
        changeLogs.associate {
            it.filePath to it.content
        }
    }

    override val masterChangeLogFiles: List<String>
        get() = changeLogs
            // TODO - ensure validation that a CPK with changelog files has at least 1 master file
            //  must happen during CPI installation.
            .filter {
                it.filePath == MASTER_CHANGE_LOG
            }.map {
                it.filePath
            }

    override val changeLogFileList: Set<String>
        get() = all.keys

    override fun fetch(path: String): InputStream {
        return all[path]?.byteInputStream()
            ?: throw CordaRuntimeException("Cannot find changelog file: $path")
    }
}