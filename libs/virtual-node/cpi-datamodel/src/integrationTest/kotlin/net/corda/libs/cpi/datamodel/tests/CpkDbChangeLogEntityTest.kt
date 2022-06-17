package net.corda.libs.cpi.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogKey
import net.corda.libs.cpi.datamodel.findCpkDbChangeLog
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpkDbChangeLogEntityTest {
    private val dbConfig: EntityManagerConfiguration =
        DbUtils.getEntityManagerConfiguration("cpk_changelog_db")

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
    private fun cleanUp() {
        dbConfig.close()
    }

    @Test
    fun `can persist changelogs`() {
        val (cpi, cpk) = TestObject.createCpiWithCpk()

        val changeLog1 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk.id.cpkName, cpk.id.cpkVersion, cpk.id.cpkSignerSummaryHash, "master"),
            "master-checksum",
            "master-content"
        )
        val changeLog2 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk.id.cpkName, cpk.id.cpkVersion, cpk.id.cpkSignerSummaryHash, "other"),
            "other-checksum",
            "other-content"
        )

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.persist(cpi)
                it.persist(changeLog1)
                it.persist(changeLog2)
                it.flush()
            }
        }

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use {
            val loadedDbLogEntity = it.find(
                CpkDbChangeLogEntity::class.java,
                CpkDbChangeLogKey(cpk.id.cpkName, cpk.id.cpkVersion, cpk.id.cpkSignerSummaryHash, "master")
            )

            assertThat(changeLog1.content).isEqualTo(loadedDbLogEntity.content)
        }
    }

    @Test
    fun `can persist changelogs to existing CPI`() {
        val (cpi, cpk) = TestObject.createCpiWithCpk()

        val changeLog1 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk.id.cpkName, cpk.id.cpkVersion, cpk.id.cpkSignerSummaryHash, "master"),
            "master-checksum",
            "master-content"
        )

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.persist(cpi)
                it.flush()
            }
        }

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.persist(changeLog1)
                it.flush()
            }
        }

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use {
            val loadedDbLogEntity = it.find(
                CpkDbChangeLogEntity::class.java,
                CpkDbChangeLogKey(cpk.id.cpkName, cpk.id.cpkVersion, cpk.id.cpkSignerSummaryHash, "master")
            )

            assertThat(changeLog1.content).isEqualTo(loadedDbLogEntity.content)
        }
    }

    @Test
    fun `findCpkDbChangeLog returns all for cpk`() {
        val (cpi1, cpk1) = TestObject.createCpiWithCpk()
        val (cpi2, cpk2) = TestObject.createCpiWithCpk()

        val changeLog1 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk1.id.cpkName, cpk1.id.cpkVersion, cpk1.id.cpkSignerSummaryHash, "master"),
            "master-checksum",
            "master-content"
        )
        val changeLog2 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk1.id.cpkName, cpk1.id.cpkVersion, cpk1.id.cpkSignerSummaryHash, "other"),
            "other-checksum",
            "other-content"
        )
        val changeLog3 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk2.id.cpkName, cpk2.id.cpkVersion, cpk2.id.cpkSignerSummaryHash, "master"),
            "master-checksum",
            "master-content"
        )

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.persist(cpi1)
                it.persist(cpi2)
                it.persist(changeLog1)
                it.persist(changeLog2)
                it.persist(changeLog3)
                it.flush()
            }
        }

        val changeLogs = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.findCpkDbChangeLog(cpk1.id.cpkName, cpk1.id.cpkVersion, cpk1.id.cpkSignerSummaryHash)
        }

        assertThat(changeLogs.size).isEqualTo(2)
        assertThat(changeLogs.map { it.id }).containsAll(listOf(changeLog1.id, changeLog2.id))
    }
}