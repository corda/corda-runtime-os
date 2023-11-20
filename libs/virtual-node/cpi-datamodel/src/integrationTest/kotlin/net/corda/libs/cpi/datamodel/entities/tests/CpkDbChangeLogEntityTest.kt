package net.corda.libs.cpi.datamodel.entities.tests

import net.corda.crypto.core.parseSecureHash
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAudit
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.entities.tests.utils.cpi
import net.corda.libs.cpi.datamodel.entities.tests.utils.cpk
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.test.util.dsl.entities.cpx.cpkDbChangeLog
import net.corda.test.util.dsl.entities.cpx.cpkDbChangeLogAudit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpkDbChangeLogEntityTest {

    private val dbConfig: EntityManagerConfiguration =
        DbUtils.getEntityManagerConfiguration("cpk_changelog_db")
    private val emf = EntityManagerFactoryFactoryImpl().create(
        "test_unit",
        CpiEntities.classes.toList(),
        dbConfig
    )

    private val cpkDbChangeLogRepository = CpiCpkRepositoryFactory().createCpkDbChangeLogRepository()
    private val cpkDbChangeLogAuditRepository = CpiCpkRepositoryFactory().createCpkDbChangeLogAuditRepository()

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
        emf.close()
    }

    @Test
    fun `can persist changelogs and query with given cpk checksums`() {
        val (cpi, cpks) = TestObject.createCpiWithCpks(2)
        val cpk1 = cpks[0]
        val cpk2 = cpks[1]
        val changeLog1 = CpkDbChangeLog(
            CpkDbChangeLogIdentifier(parseSecureHash(cpk1.id.cpkFileChecksum), "master"),
            "master-content"
        )
        val changeLog2 = CpkDbChangeLog(
            CpkDbChangeLogIdentifier(parseSecureHash(cpk2.id.cpkFileChecksum), "other"),
            "other-content"
        )

        emf.transaction {
            it.persist(cpi)
            cpkDbChangeLogRepository.put(it, changeLog1)
            cpkDbChangeLogRepository.put(it, changeLog2)
        }

        emf.transaction {
            val queriedChangelogs = cpkDbChangeLogRepository.findByFileChecksum(
                it,
                setOf(cpk1.id.cpkFileChecksum, cpk2.id.cpkFileChecksum)
            ).groupBy { it.id.cpkFileChecksum }

            assertThat(queriedChangelogs.keys).isEqualTo(
                setOf(
                    parseSecureHash(cpk1.id.cpkFileChecksum),
                    parseSecureHash(cpk2.id.cpkFileChecksum)
                )
            )
            assertThat(queriedChangelogs[parseSecureHash(cpk1.id.cpkFileChecksum)]!!.size).isEqualTo(1)
            assertThat(queriedChangelogs[parseSecureHash(cpk2.id.cpkFileChecksum)]!!.size).isEqualTo(1)
            assertThat(queriedChangelogs[parseSecureHash(cpk1.id.cpkFileChecksum)]!![0]).isEqualTo(changeLog1)
            assertThat(queriedChangelogs[parseSecureHash(cpk2.id.cpkFileChecksum)]!![0]).isEqualTo(changeLog2)
        }
    }

    @Test
    fun `can persist changelogs and query for all CPKs associated with the CPI`() {
        val (cpi, cpks) = TestObject.createCpiWithCpks(2)
        val cpk1 = cpks[0]
        val cpk2 = cpks[1]
        val changeLog1 = CpkDbChangeLog(
            CpkDbChangeLogIdentifier(parseSecureHash(cpk1.id.cpkFileChecksum), "master"),
            "master-content"
        )
        val changeLog2 = CpkDbChangeLog(
            CpkDbChangeLogIdentifier(parseSecureHash(cpk2.id.cpkFileChecksum), "other"),
            "other-content"
        )
        val (unrelatedCpi, unrelatedCpks) = TestObject.createCpiWithCpks(1)
        val changeLog3 = CpkDbChangeLog(
            CpkDbChangeLogIdentifier(parseSecureHash(unrelatedCpks[0].id.cpkFileChecksum), "unrelated-file-path"),
            "unrelated-content"
        )

        emf.transaction {
            it.persist(cpi)
            cpkDbChangeLogRepository.put(it, changeLog1)
            cpkDbChangeLogRepository.put(it, changeLog2)
            it.persist(unrelatedCpi)
            cpkDbChangeLogRepository.put(it, changeLog3)
        }

        emf.transaction {
            val queriedChangelogs1 = cpkDbChangeLogRepository.findByCpiId(
                it,
                CpiIdentifier(cpi.name, cpi.version, parseSecureHash(cpi.signerSummaryHash))
            ).toSet()
            assertThat(queriedChangelogs1).isEqualTo(setOf(changeLog1, changeLog2))

            val queriedChangelogs2 = cpkDbChangeLogRepository.findByCpiId(
                it,
                CpiIdentifier(unrelatedCpi.name, unrelatedCpi.version, parseSecureHash(unrelatedCpi.signerSummaryHash))
            ).toSet()
            assertThat(queriedChangelogs2).isEqualTo(setOf(changeLog3))
        }
    }

    @Test
    fun `can persist CPK changelog audit trail`() {
        val audit = cpkDbChangeLogAudit { }

        emf.transaction {
            cpkDbChangeLogAuditRepository.put(it, audit)
        }

        emf.transaction {
            val loadedDbLog = cpkDbChangeLogAuditRepository.findById(it, audit.id)
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
        val cpiSignerSummaryHash = TestObject.genRandomChecksum()
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
            fileChecksum(parseSecureHash(cpk1.cpkFileChecksum))
            filePath("cpk1.xml")
        }
        val changelog2 = cpkDbChangeLog {
            fileChecksum(parseSecureHash(cpk2.cpkFileChecksum))
            filePath("cpk2.xml")
        }
        val changelog3 = cpkDbChangeLog {
            fileChecksum(parseSecureHash(cpk3.cpkFileChecksum))
            filePath("cpk3.xml")
        }
        val sharedChangelog = cpkDbChangeLog {
            fileChecksum(parseSecureHash(sharedCpk.cpkFileChecksum))
            filePath("shared_$cpkRand.xml")
        }

        emf.transaction {
            it.persist(originalCpi)
            cpkDbChangeLogRepository.put(it, changelog1)
            cpkDbChangeLogAuditRepository.put(it, cpkDbChangeLogAudit(changelog1))
            cpkDbChangeLogRepository.put(it, changelog2)
            cpkDbChangeLogAuditRepository.put(it, cpkDbChangeLogAudit(changelog2))
            cpkDbChangeLogRepository.put(it, changelog3)
            cpkDbChangeLogAuditRepository.put(it, cpkDbChangeLogAudit(changelog3))
            cpkDbChangeLogRepository.put(it, sharedChangelog)
            cpkDbChangeLogAuditRepository.put(it, cpkDbChangeLogAudit(sharedChangelog))
        }

        emf.transaction {
            val currentCpkChangelogs = cpkDbChangeLogRepository.findByCpiId(
                it,
                CpiIdentifier(cpiName, cpiVersion, cpiSignerSummaryHash)
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
            fileChecksum(parseSecureHash(cpk5.cpkFileChecksum))
            filePath("cpk5.xml")
        }
        val changelog6 = cpkDbChangeLog {
            fileChecksum(parseSecureHash(cpk6.cpkFileChecksum))
            filePath("cpk6.xml")
        }

        emf.transaction {
            it.merge(updatedCpi)
            cpkDbChangeLogRepository.update(it, changelog5)
            cpkDbChangeLogAuditRepository.put(it, cpkDbChangeLogAudit(changelog5))
            cpkDbChangeLogRepository.update(it, changelog6)
            cpkDbChangeLogAuditRepository.put(it, cpkDbChangeLogAudit(changelog6))
            // simulating persisting changelog for shared CPK again
            cpkDbChangeLogRepository.update(it, sharedChangelog)
            cpkDbChangeLogAuditRepository.put(it, cpkDbChangeLogAudit(sharedChangelog))
        }

        emf.transaction {
            val loadedCpkChangelogs = cpkDbChangeLogRepository.findByCpiId(
                it,
                CpiIdentifier(cpiName, cpiVersion, cpiSignerSummaryHash)
            ).toSet()

            assertThat(loadedCpkChangelogs).isEqualTo(setOf(changelog5, changelog6, sharedChangelog))
        }
    }

    @Test
    fun `findCpkDbChangeLog with no changelogs`() {
        val (cpi1, _) = TestObject.createCpiWithCpks(2)
        val (cpi2, _) = TestObject.createCpiWithCpks()

        emf.transaction {
            it.persist(cpi1)
            it.persist(cpi2)

            val changeLogs = cpkDbChangeLogRepository.findByCpiId(
                it,
                CpiIdentifier(cpi1.name, cpi1.version, parseSecureHash(cpi1.signerSummaryHash))
            )
            assertThat(changeLogs).isEmpty()
        }
    }
}
