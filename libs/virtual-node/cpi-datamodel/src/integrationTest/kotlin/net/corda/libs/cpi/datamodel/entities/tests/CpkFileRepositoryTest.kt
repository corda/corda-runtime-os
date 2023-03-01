package net.corda.libs.cpi.datamodel.entities.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.persistence.EntityManager
import net.corda.v5.crypto.SecureHash
import java.util.*
import net.corda.libs.cpi.datamodel.entities.internal.CpkFileEntity
import net.corda.libs.cpi.datamodel.repository.CpkFileRepositoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpkFileRepositoryTest {

    private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("cpk_file_db")

    private val cpkFileRepository = CpkFileRepositoryImpl()
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

    @BeforeEach
    fun clearCpkFileTable(){
        // This is required because when the tests run using Jenkins a real database is used and the data
        // is not wiped out. The tests in this class require a clean CpkFileEntity table.
        transaction {
            createQuery("DELETE FROM ${CpkFileEntity::class.simpleName}").executeUpdate()
        }
    }

    @AfterAll
    fun cleanUp() {
        dbConfig.close()
    }

    @Test
    fun `can persist cpk files and query with given cpk checksum`() {
        transaction {
            //Create CPK files
            val cpkFile1 = TestObject.genRandomCpkFile()
            val cpkFile2 = TestObject.genRandomCpkFile()
            val cpkFile3 = TestObject.genRandomCpkFile()

            // Create CPKs
            val cpkMetadataEntity1 = TestObject.createCpk(
                cpkFile1.fileChecksum.toString(),
                "test-cpk",
                "2.2.3",
                TestObject.genRandomChecksum().toString()
            )

            val cpkMetadataEntity2 = TestObject.createCpk(
                cpkFile2.fileChecksum.toString(),
                "test-cpk2",
                "2.2.4",
                TestObject.genRandomChecksum().toString()
            )

            val cpkMetadataEntity3 = TestObject.createCpk(
                cpkFile3.fileChecksum.toString(),
                "test-cpk3",
                "2.2.5",
                TestObject.genRandomChecksum().toString()
            )

            // Update database
            persist(cpkMetadataEntity1)
            persist(cpkMetadataEntity2)
            persist(cpkMetadataEntity3)
            cpkFileRepository.put(this, cpkFile1)
            cpkFileRepository.put(this, cpkFile2)
            cpkFileRepository.put(this, cpkFile3)

            // Query database
            val queriedCpkFile = cpkFileRepository.findById(this, cpkFile1.fileChecksum)
            assertThat(queriedCpkFile).isEqualTo(cpkFile1)

            val queriedCpkFile2 = cpkFileRepository.findById(this, cpkFile2.fileChecksum)
            assertThat(queriedCpkFile2).isEqualTo(cpkFile2)

            val queriedCpkFile3 = cpkFileRepository.findById(this, listOf(cpkFile1.fileChecksum, cpkFile3.fileChecksum))
            assertThat(queriedCpkFile3.size).isEqualTo(2)
            assertThat(queriedCpkFile3).containsAll(listOf(cpkFile1, cpkFile3))
        }
    }

    @Test
    fun `can persist cpk files and check if a cpk file exists`() {
        transaction {
            //Create CPK files
            val cpkFile1 = TestObject.genRandomCpkFile()

            // Create CPKs
            val cpkMetadataEntity1 = TestObject.createCpk(
                cpkFile1.fileChecksum.toString(),
                "test-cpk",
                "2.2.3",
                TestObject.genRandomChecksum().toString()
            )

            // Update database
            persist(cpkMetadataEntity1)
            cpkFileRepository.put(this, cpkFile1)

            // Query database
            assertThat(cpkFileRepository.exists(this, cpkFile1.fileChecksum)).isTrue
            assertThat(cpkFileRepository.exists(this, SecureHash("SHA1", "DUMMY".toByteArray()))).isFalse
        }
    }

    @Test
    fun `can persist cpk files and retrive all cpk files`() {
        transaction {
            this.createQuery("delete from ${CpkFileEntity::class.simpleName}").executeUpdate()
            //Create CPK files
            val cpkFile1 = TestObject.genRandomCpkFile()
            val cpkFile2 = TestObject.genRandomCpkFile()

            // Create CPKs
            val cpkMetadataEntity1 = TestObject.createCpk(
                cpkFile1.fileChecksum.toString(),
                "test-cpk",
                "2.2.3",
                TestObject.genRandomChecksum().toString()
            )

            val cpkMetadataEntity2 = TestObject.createCpk(
                cpkFile2.fileChecksum.toString(),
                "test-cpk2",
                "2.2.4",
                TestObject.genRandomChecksum().toString()
            )

            // Update database
            persist(cpkMetadataEntity1)
            persist(cpkMetadataEntity2)
            cpkFileRepository.put(this, cpkFile1)
            cpkFileRepository.put(this, cpkFile2)

            // Query database
            val queriedCpkFiles = cpkFileRepository.findAll(this)
            assertThat(queriedCpkFiles.size).isEqualTo(2)
            assertThat(queriedCpkFiles).containsAll(listOf(cpkFile1, cpkFile2))
        }
    }

    @Test
    fun `all cpk files are retrieved excepted the filtered ones`() {
        transaction {
            //Create CPK files
            val cpkFile1 = TestObject.genRandomCpkFile()
            val cpkFile2 = TestObject.genRandomCpkFile()
            val cpkFile3 = TestObject.genRandomCpkFile()

            // Create CPKs
            val cpkMetadataEntity1 = TestObject.createCpk(
                cpkFile1.fileChecksum.toString(),
                "test-cpk",
                "2.2.3",
                TestObject.genRandomChecksum().toString()
            )

            val cpkMetadataEntity2 = TestObject.createCpk(
                cpkFile2.fileChecksum.toString(),
                "test-cpk2",
                "2.2.4",
                TestObject.genRandomChecksum().toString()
            )

            val cpkMetadataEntity3 = TestObject.createCpk(
                cpkFile3.fileChecksum.toString(),
                "test-cpk3",
                "2.2.5",
                TestObject.genRandomChecksum().toString()
            )

            // Update database
            persist(cpkMetadataEntity1)
            persist(cpkMetadataEntity2)
            persist(cpkMetadataEntity3)
            cpkFileRepository.put(this, cpkFile1)
            cpkFileRepository.put(this, cpkFile2)
            cpkFileRepository.put(this, cpkFile3)

            // Query database
            //  All cpk files were excluded
            val queriedCpkFiles1 = cpkFileRepository.findAll(
                this,
                listOf(cpkFile1.fileChecksum, cpkFile2.fileChecksum, cpkFile3.fileChecksum)
            )
            assertThat(queriedCpkFiles1.size).isEqualTo(0)

            val queriedCpkFiles2 =
                cpkFileRepository.findAll(
                    this,
                    fileChecksumsToExclude = listOf(cpkFile1.fileChecksum, cpkFile3.fileChecksum)
                )
            assertThat(queriedCpkFiles2.size).isEqualTo(1)
            assertThat(queriedCpkFiles2).contains(cpkFile2)

            val queriedCpkFiles3 =
                cpkFileRepository.findAll(this, fileChecksumsToExclude = listOf(cpkFile1.fileChecksum))
            assertThat(queriedCpkFiles3.size).isEqualTo(2)
            assertThat(queriedCpkFiles3).containsAll(listOf(cpkFile2, cpkFile3))

            // Order should not matter
            val queriedCpkFiles4 =
                cpkFileRepository.findAll(
                    this,
                    fileChecksumsToExclude = listOf(cpkFile3.fileChecksum, cpkFile1.fileChecksum)
                )
            assertThat(queriedCpkFiles4.size).isEqualTo(1)
            assertThat(queriedCpkFiles4).contains(cpkFile2)
        }
    }
}
