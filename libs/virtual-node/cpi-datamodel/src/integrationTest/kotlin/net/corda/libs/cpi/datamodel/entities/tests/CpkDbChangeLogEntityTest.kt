package net.corda.libs.cpi.datamodel.entities.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAudit
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogAuditRepositoryImpl
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepositoryImpl
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import javax.persistence.EntityManager
import net.corda.test.util.dsl.entities.cpx.cpi
import net.corda.test.util.dsl.entities.cpx.cpk
import net.corda.test.util.dsl.entities.cpx.cpkDbChangeLog
import net.corda.test.util.dsl.entities.cpx.cpkDbChangeLogAudit
import net.corda.v5.crypto.SecureHash
import java.util.*
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpkDbChangeLogEntityTest {

    private val dbConfig: EntityManagerConfiguration =
        DbUtils.getEntityManagerConfiguration("cpk_changelog_db")

    private val cpkDbChangeLogRepository = CpkDbChangeLogRepositoryImpl()
    private val cpkDbChangeLogAuditRepository = CpkDbChangeLogAuditRepositoryImpl()
    private fun transaction(callback: EntityManager.() -> Unit): Unit = EntityManagerFactoryFactoryImpl().create(
        "test_unit",
        CpiEntities.classes.toList(),
        dbConfig
    ).use { em ->
        em.transaction {
            it.callback()
        }
    }

    init {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/config/db.changelog-master.xml"),
                    DbSchema::class.java.classLoader
                )
            )
        )
        dbConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
    }

    @AfterAll
    fun cleanUp() {
        dbConfig.close()
    }

    @Test
    fun `can persist changelogs and query with given cpk checksums`() {
        val (cpi, cpks) = TestObject.createCpiWithCpks(2)
        val cpk1 = cpks[0]
        val cpk2 = cpks[1]
        val changeLog1 = CpkDbChangeLog(
            CpkDbChangeLogIdentifier(cpk1.id.cpkFileChecksum,"master"),
            "master-content"
        )
        val changeLog2 = CpkDbChangeLog(
            CpkDbChangeLogIdentifier(cpk2.id.cpkFileChecksum,"other"),
            "other-content"
        )

        transaction {
            persist(cpi)
            cpkDbChangeLogRepository.put(this, changeLog1)
            cpkDbChangeLogRepository.put(this, changeLog2)
        }

        transaction {
            val queriedChangelogs = cpkDbChangeLogRepository.findByFileChecksum(this, setOf(cpk1.id.cpkFileChecksum, cpk2.id.cpkFileChecksum)).groupBy { it.id.cpkFileChecksum }

            assertThat(queriedChangelogs.keys).isEqualTo(setOf(cpk1.id.cpkFileChecksum, cpk2.id.cpkFileChecksum))
            assertThat(queriedChangelogs[cpk1.id.cpkFileChecksum]!!.size).isEqualTo(1)
            assertThat(queriedChangelogs[cpk2.id.cpkFileChecksum]!!.size).isEqualTo(1)
            assertThat(queriedChangelogs[cpk1.id.cpkFileChecksum]!![0]).isEqualTo(changeLog1)
            assertThat(queriedChangelogs[cpk2.id.cpkFileChecksum]!![0]).isEqualTo(changeLog2)
        }
    }

    @Test
    fun `can persist changelogs and query for all CPKs associated with the CPI`() {
        val (cpi, cpks) = TestObject.createCpiWithCpks(2)
        val cpk1 = cpks[0]
        val cpk2 = cpks[1]
        val changeLog1 = CpkDbChangeLog(
            CpkDbChangeLogIdentifier(cpk1.id.cpkFileChecksum, "master"),
            "master-content"
        )
        val changeLog2 = CpkDbChangeLog(
            CpkDbChangeLogIdentifier(cpk2.id.cpkFileChecksum, "other"),
            "other-content"
        )
        val (unrelatedCpi, unrelatedCpks) = TestObject.createCpiWithCpks(1)
        val changeLog3 = CpkDbChangeLog(
            CpkDbChangeLogIdentifier( unrelatedCpks[0].id.cpkFileChecksum,"unrelated-file-path"),
            "unrelated-content"
        )

        transaction {
            persist(cpi)
            cpkDbChangeLogRepository.put(this, changeLog1)
            cpkDbChangeLogRepository.put(this, changeLog2)
            persist(unrelatedCpi)
            cpkDbChangeLogRepository.put(this, changeLog3)
        }

        transaction {
            val queriedChangelogs1 = cpkDbChangeLogRepository.findByCpiId(
                this,
                CpiIdentifier(cpi.name, cpi.version, SecureHash.parse(cpi.signerSummaryHash))
            ).toSet()
            assertThat(queriedChangelogs1).isEqualTo(setOf(changeLog1, changeLog2))

            val queriedChangelogs2 =  cpkDbChangeLogRepository.findByCpiId(
                this,
                CpiIdentifier(unrelatedCpi.name, unrelatedCpi.version, SecureHash.parse(unrelatedCpi.signerSummaryHash))
            ).toSet()
            assertThat(queriedChangelogs2).isEqualTo(setOf(changeLog3))
        }
    }

    @Test
    fun `can persist CPK changelog audit trail`() {
        val audit = cpkDbChangeLogAudit {  }

        transaction {
            cpkDbChangeLogAuditRepository.put(this, audit)
        }

        transaction {
           val loadedDbLog = cpkDbChangeLogAuditRepository.findById(this, audit.id)
           assertThat(loadedDbLog).isEqualTo(audit)
        }
    }


    private fun cpkDbChangeLogAudit(changeLog: CpkDbChangeLog): CpkDbChangeLogAudit {
        return cpkDbChangeLogAudit {
            fileChecksum(changeLog.id.cpkFileChecksum)
            filePath(changeLog.id.filePath)
            content(changeLog.content)
        }
    }

    @Test
    fun `when CPI is merged with new CPKs, old orphaned CPK changesets are no longer associated with the CPI and shared CPK works`() {
        val cpiName = UUID.randomUUID().toString()
        val cpiVersion = UUID.randomUUID().toString()
        val cpiSignerSummaryHash = TestObject.genRandomChecksum().toString()
        val cpkRand = UUID.randomUUID()

        val cpk1 = cpk { }
        val cpk2 = cpk { }
        val cpk3 = cpk { }
        val sharedCpk = cpk {
            instanceId(cpkRand)
        }
        val originalCpi = cpi {
            name(cpiName)
            version(cpiVersion)
            signerSummaryHash(cpiSignerSummaryHash)
            cpk(cpk1) {
                fileName("cpk1.xml")
            }
            cpk(cpk2) {
                fileName("cpk2.xml")
            }
            cpk(cpk3) {
                fileName("cpk3.xml")
            }
            cpk(sharedCpk) {
                fileName("shared_$cpkRand.xml")
            }
        }

        val changelog1 = cpkDbChangeLog {
            fileChecksum(cpk1.cpkFileChecksum)
            filePath("cpk1.xml")
        }
        val changelog2 = cpkDbChangeLog {
            fileChecksum(cpk2.cpkFileChecksum)
            filePath("cpk2.xml")
        }
        val changelog3 = cpkDbChangeLog {
            fileChecksum(cpk3.cpkFileChecksum)
            filePath("cpk3.xml")
        }
        val sharedChangelog = cpkDbChangeLog {
            fileChecksum(sharedCpk.cpkFileChecksum)
            filePath("shared_$cpkRand.xml")
        }

        transaction {
            persist(originalCpi)
            cpkDbChangeLogRepository.put(this,changelog1)
            cpkDbChangeLogAuditRepository.put(this, cpkDbChangeLogAudit(changelog1))
            cpkDbChangeLogRepository.put(this,changelog2)
            cpkDbChangeLogAuditRepository.put(this, cpkDbChangeLogAudit(changelog2))
            cpkDbChangeLogRepository.put(this,changelog3)
            cpkDbChangeLogAuditRepository.put(this, cpkDbChangeLogAudit(changelog3))
            cpkDbChangeLogRepository.put(this,sharedChangelog)
            cpkDbChangeLogAuditRepository.put(this, cpkDbChangeLogAudit(sharedChangelog))
        }

        transaction {
            val currentCpkChangelogs = cpkDbChangeLogRepository.findByCpiId(
                this,
                CpiIdentifier(cpiName, cpiVersion, SecureHash.parse(cpiSignerSummaryHash))
            ).toSet()

            assertThat(currentCpkChangelogs).isEqualTo(setOf(changelog1, changelog2, changelog3, sharedChangelog))
        }

        // This CPI has same PK as original, but different set of CPKs. When we merge this on top of original, we will see the old CpiCpk
        // relationships get purged (as a result of orphanRemoval). In the assertion we should see the correct set of dbChangeLogs returned
        val cpk5 = cpk {}
        val cpk6 = cpk {}
        val updatedCpi = cpi {
            name(cpiName)
            version(cpiVersion)
            signerSummaryHash(cpiSignerSummaryHash)
            cpk(cpk5) {
                fileName("cpk5.xml")
            }
            cpk(cpk6) {
                fileName("cpk6.xml")
            }
            cpk(sharedCpk) {
                fileName("shared_$cpkRand.xml")
            }
        }

        val changelog5 = cpkDbChangeLog {
            fileChecksum(cpk5.cpkFileChecksum)
            filePath("cpk5.xml")
        }
        val changelog6 = cpkDbChangeLog {
            fileChecksum(cpk6.cpkFileChecksum)
            filePath("cpk6.xml")
        }

        transaction {
            merge(updatedCpi)
            cpkDbChangeLogRepository.update(this, changelog5)
            cpkDbChangeLogAuditRepository.put(this, cpkDbChangeLogAudit(changelog5))
            cpkDbChangeLogRepository.update(this, changelog6)
            cpkDbChangeLogAuditRepository.put(this, cpkDbChangeLogAudit(changelog6))
            // simulating persisting changelog for shared CPK again
            cpkDbChangeLogRepository.update(this, sharedChangelog)
            cpkDbChangeLogAuditRepository.put(this, cpkDbChangeLogAudit(sharedChangelog))
        }

        transaction {
            val loadedCpkChangelogs = cpkDbChangeLogRepository.findByCpiId(
                this,
                CpiIdentifier(cpiName, cpiVersion, SecureHash.parse(cpiSignerSummaryHash))
            ).toSet()

            assertThat(loadedCpkChangelogs).isEqualTo(setOf(changelog5, changelog6, sharedChangelog))
        }
    }

    @Test
    fun `findCpkDbChangeLog with no changelogs`() {
        val (cpi1, _) = TestObject.createCpiWithCpks(2)
        val (cpi2, _) = TestObject.createCpiWithCpks()

        transaction {
            persist(cpi1)
            persist(cpi2)

            val changeLogs = cpkDbChangeLogRepository.findByCpiId(
                this,
                CpiIdentifier(cpi1.name, cpi1.version, SecureHash.parse(cpi1.signerSummaryHash))
            )
            assertThat(changeLogs).isEmpty()
        }
    }
}
