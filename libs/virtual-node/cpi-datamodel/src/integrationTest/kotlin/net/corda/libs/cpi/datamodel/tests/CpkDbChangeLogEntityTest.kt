package net.corda.libs.cpi.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogKey
import net.corda.libs.cpi.datamodel.findDbChangeLogForCpi
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.persistence.EntityManager

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpkDbChangeLogEntityTest {
    private val dbConfig: EntityManagerConfiguration =
        DbUtils.getEntityManagerConfiguration("cpk_changelog_db")

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
    fun `can persist changelogs`() {
        val (cpi, cpks) = TestObject.createCpiWithCpks()
        val cpk = cpks.first()
        val changeLog1 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk.metadata.id.cpkName, cpk.metadata.id.cpkVersion,
                cpk.metadata.id.cpkSignerSummaryHash, "master"),
            "master-checksum",
            "master-content"
        )
        val changeLog2 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk.metadata.id.cpkName, cpk.metadata.id.cpkVersion,
                cpk.metadata.id.cpkSignerSummaryHash, "other"),
            "other-checksum",
            "other-content"
        )

        transaction {
            persist(cpi)
            persist(changeLog1)
            persist(changeLog2)
            flush()
        }

        transaction {
           val loadedDbLogEntity = find(
                CpkDbChangeLogEntity::class.java,
                CpkDbChangeLogKey(cpk.metadata.id.cpkName, cpk.metadata.id.cpkVersion,
                    cpk.metadata.id.cpkSignerSummaryHash, "master")
            )

            assertThat(changeLog1.content).isEqualTo(loadedDbLogEntity.content)
        }
    }

    @Test
    fun `can persist changelogs to existing CPI`() {
        val (cpi, cpks) = TestObject.createCpiWithCpks()
        val cpk = cpks.first()
        val changeLog1 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk.metadata.id.cpkName, cpk.metadata.id.cpkVersion, cpk.metadata.id.cpkSignerSummaryHash, "master"),
            "master-checksum",
            "master-content"
        )

        transaction {
            persist(cpi)
            flush()
        }

        transaction {
            persist(changeLog1)
            flush()
        }

        transaction {
            val loadedDbLogEntity = find(
                CpkDbChangeLogEntity::class.java,
                CpkDbChangeLogKey(cpk.metadata.id.cpkName, cpk.metadata.id.cpkVersion, cpk.metadata.id.cpkSignerSummaryHash, "master")
            )

            assertThat(changeLog1.content).isEqualTo(loadedDbLogEntity.content)
        }
    }

    @Test
    fun `findCpkDbChangeLog returns all for cpk`() {
        val (cpi1, cpks1) = TestObject.createCpiWithCpks(2)
        val (cpi2, cpks2) = TestObject.createCpiWithCpks()
        val cpk1 = cpks1.first()
        val cpk1b = cpks1[1]
        val cpk2 = cpks2.first()

        val changeLog1 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk1.metadata.id.cpkName, cpk1.metadata.id.cpkVersion, cpk1.metadata.id.cpkSignerSummaryHash, "master"),
            "master-checksum",
            """<databaseChangeLog xmlns="https://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="https://www.liquibase.org/xml/ns/dbchangelog https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">

    <changeSet author="R3.Corda" id="test-migrations-v1.0">
        <createTable tableName="test">
             <column name="id" type="varchar(8)">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>
</databaseChangeLog>"""
        )
        val changeLog1b = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk1b.metadata.id.cpkName, cpk1b.metadata.id.cpkVersion, cpk1b.metadata.id.cpkSignerSummaryHash, "master"),
            "master-checksum",
            """<databaseChangeLog xmlns="https://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="https://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="https://www.liquibase.org/xml/ns/dbchangelog https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">

    <changeSet author="R3.Corda" id="test-migrations-v1.0">
        <createTable tableName="test">
        <addColumn tableName="person" >
                <column name="is_active" type="varchar2(1)" defaultValue="Y" />  
            </addColumn>  
        </createTable>
    </changeSet>
</databaseChangeLog>"""
        )
        val changeLog2 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk1.metadata.id.cpkName, cpk1.metadata.id.cpkVersion, cpk1.metadata.id.cpkSignerSummaryHash, "other"),
            "other-checksum",
            "other-content"
        )
        val changeLog3 = CpkDbChangeLogEntity(
            CpkDbChangeLogKey(cpk2.metadata.id.cpkName, cpk2.metadata.id.cpkVersion, cpk2.metadata.id.cpkSignerSummaryHash, "master"),
            "master-checksum",
            "master-content"
        )

        transaction {
            persist(cpi1)
            persist(cpi2)
            persist(changeLog1)
            persist(changeLog1b)
            persist(changeLog2)
            persist(changeLog3)
            flush()
            val changeLogs = findDbChangeLogForCpi(
                this,
                CpiIdentifier(cpi1.name, cpi1.version, SecureHash.parse(cpi1.signerSummaryHash))
            )
            assertThat(changeLogs.size).isEqualTo(3)
            assertThat(changeLogs.map { it.id }).containsAll(listOf(changeLog1.id, changeLog1b.id, changeLog2.id))
        }
    }

    @Test
    fun `findCpkDbChangeLog with no changelogs`() {
        val (cpi1, _) = TestObject.createCpiWithCpks(2)
        val (cpi2, _) = TestObject.createCpiWithCpks()

        transaction {
            persist(cpi1)
            persist(cpi2)
            flush()
            val changeLogs = findDbChangeLogForCpi(
                this,
                CpiIdentifier(cpi1.name, cpi1.version, SecureHash("SHA1", cpi1.signerSummaryHash.toByteArray()))
            )
            assertThat(changeLogs).isEmpty()
        }
    }
}