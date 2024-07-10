package net.corda.libs.cpi.datamodel.entities.tests

import net.corda.crypto.core.SecureHashImpl
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.entities.internal.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.internal.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpiMetadataRepositoryImplTest {

    private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("cpk_file_db")
    private val emf = EntityManagerFactoryFactoryImpl().create(
        "test_unit",
        CpiEntities.classes.toList(),
        dbConfig
    )

    private val cpiMetadataRepository = CpiCpkRepositoryFactory().createCpiMetadataRepository()

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
        //The CpiMetaDataEntity table is linked to the CpiCpkEntity table
        //If we delete the CpiMetadataEntity first, failures occur due to the foreign key constraints
        //So the deletion of the CpiCpkEntity table is needed to be deleted first before the CpiMetadataEntity
        emf.transaction {
            it.createQuery("DELETE FROM ${CpiCpkEntity::class.simpleName}").executeUpdate()
            it.createQuery("DELETE FROM ${CpiMetadataEntity::class.simpleName}").executeUpdate()
        }
    }

    @AfterAll
    fun cleanUp() {
        emf.close()
    }

    @Test
    fun `put stores CPI MetaData without any cpks successfully`(){
        emf.transaction {
            val hashValue = SecureHashImpl("SHA-256", byteArrayOf(0))
            val cpiIndentifier = CpiIdentifier("test","1.0", hashValue)
            cpiMetadataRepository.put(
                em = it,
                cpiId = cpiIndentifier,
                cpiFileName = "filename",
                fileChecksum = hashValue,
                groupId = "group",
                groupPolicy = "group-policy",
                fileUploadRequestId = "uploadRequestId",
                cpks = listOf()
            )

            val cpiData = cpiMetadataRepository.findAll(em = it)
            assertThat(cpiData).isNotEmpty
        }
    }
}