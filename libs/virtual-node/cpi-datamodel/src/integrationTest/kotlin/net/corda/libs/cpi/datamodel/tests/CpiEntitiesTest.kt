package net.corda.libs.cpi.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.CpkDataEntity
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import net.corda.libs.cpi.datamodel.CpkMetadataEntityKey
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpiEntitiesIntegrationTest {
    val dbConfig: EntityManagerConfiguration

    init {
        // comment this out if you want to run the test against a real postgres
        //  NOTE: the blob storage doesn't work in HSQL, hence skipping the majority of the test.
//        System.setProperty("postgresPort", "5432")
        dbConfig = DbUtils.getEntityManagerConfiguration("cpi_db")
    }
    @BeforeAll
    private fun prepareDatabase() {


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

    @Test
    fun `can persist cpi and cpks`() {
        val cpiId = UUID.randomUUID()
        val cpi = CpiMetadataEntity(
            "test-cpi-$cpiId",
            "1.0",
            "test-cpi-hash",
            "test-cpi-$cpiId.cpi",
            "test-cpi.cpi-$cpiId-hash",
            "{group-policy-json}",
            "group-id",
            "file-upload-request-id-$cpiId"
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

        val loadedCpi = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use {
            it.find(CpiMetadataEntity::class.java,
                CpiMetadataEntityKey(cpi.name, cpi.version, cpi.signerSummaryHash))
        }

        assertThat(loadedCpi).isEqualTo(cpi)

        // HSQL doesn't support the blob storage, so skipping from here.
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping the rest of this test when run against in-memory DB.")


        // CREATE a new CPK, data & entity
        val cpkId = UUID.randomUUID()
        val cpk = CpkDataEntity(
            "cpk-checksum-$cpkId",
            ByteArray(2000),
        )
        val cpkEntity = CpkMetadataEntity(
            cpi,
            cpk.fileChecksum,
            "test-cpk.cpk",
        )

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.merge(cpk)
                it.merge(cpkEntity)
                it.flush()
            }
        }

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use {
            val loadedCpkEntity = it.find(CpkMetadataEntity::class.java, CpkMetadataEntityKey(
                cpi,
                cpkEntity.cpkFileChecksum))
            val loadedCpkDataEntity = it.find(CpkDataEntity::class.java, cpk.fileChecksum)

            assertThat(loadedCpkEntity).isEqualTo(cpkEntity)
            assertThat(loadedCpkEntity.cpi).isEqualTo(cpi)
            assertThat(loadedCpkDataEntity).isEqualTo(cpk)
        }
    }

    @Test
    fun `can add cpk to cpi`() {
        // HSQL doesn't support the blob storage, so skipping from here.
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        // Create CPI First
        val cpiId = UUID.randomUUID()
        val cpi = CpiMetadataEntity(
            "test-cpi-$cpiId",
            "1.0",
            "test-cpi-hash",
            "test-cpi-$cpiId.cpi",
            "test-cpi.cpi-$cpiId-hash",
            "{group-policy-json}",
            "group-id",
            "file-upload-request-id-$cpiId"
        )
        val cpkId = UUID.randomUUID()
        val cpk = CpkDataEntity(
            "cpk-checksum-$cpkId",
            ByteArray(2000),
        )
        val cpkEntity = CpkMetadataEntity(
            cpi,
            cpk.fileChecksum,
            "test-cpk.cpk",
        )

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                // saving cpk should also save related cpi
                it.persist(cpk)
                it.persist(cpkEntity)
                it.flush()
            }
        }

        // Create another CPK
        val cpk2 = CpkDataEntity(
            "cpk-checksum-${UUID.randomUUID()}",
            ByteArray(2000),
        )

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.persist(cpk2)
                it.flush()
            }
        }

        // add it to the existing CPI
        val cpkEntity2 = CpkMetadataEntity(
            cpi,
            cpk2.fileChecksum,
            "test-cpk2.cpk"
        )
        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.merge(cpkEntity2)
                it.flush()
            }
        }

        val loadedCpi = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use {
            it.find(CpiMetadataEntity::class.java,
                CpiMetadataEntityKey(cpi.name, cpi.version, cpi.signerSummaryHash))
        }

        // check cpks meta is eagerly loaded
        assertThat(loadedCpi.cpks).containsExactlyInAnyOrder(
            cpkEntity, cpkEntity2
        )
    }
}