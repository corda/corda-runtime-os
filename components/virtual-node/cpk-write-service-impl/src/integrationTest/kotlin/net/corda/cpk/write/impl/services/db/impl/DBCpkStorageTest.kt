package net.corda.cpk.write.impl.services.db.impl

import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpkDataEntity
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.persistence.EntityManagerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DBCpkStorageTest {
    private lateinit var dbCpkStorage: CpkStorage

    private val emConfig: EntityManagerConfiguration
    private lateinit var emFactory: EntityManagerFactory

    init {
        // comment this out if you want to run the test against a real postgres
        System.setProperty("postgresPort", "5432")
        emConfig = DbUtils.getEntityManagerConfiguration("db_cpk_storage")
    }

    companion object {
        const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"
        val DUMMY_HASH_1 = SecureHash.create("SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA")
        val DUMMY_HASH_2 = SecureHash.create("SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FB")
        val DUMMY_HASH_3 = SecureHash.create("SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FC")

        fun storeCpkDataEntity(checksum: SecureHash, bytes: ByteArray, emFactory: EntityManagerFactory) {
            val cpkDataEntity = CpkDataEntity(
                checksum.toString(),
                bytes,
            )
            emFactory.createEntityManager().transaction {
                it.persist(cpkDataEntity)
                it.flush()
            }
        }

        fun removeCpkDataEntity(checksum: SecureHash, emFactory: EntityManagerFactory) {
            emFactory.createEntityManager().transaction {
                it.createQuery(
                    "DELETE FROM ${CpkDataEntity::class.simpleName} cpk " +
                            "WHERE cpk.fileChecksum=:checksum"
                )
                    .setParameter("checksum", checksum.toString())
                    .executeUpdate()
                it.flush()
            }
        }

    }

    @BeforeAll
    fun prepareDatabase() {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf(MIGRATION_FILE_LOCATION),
                    DbSchema::class.java.classLoader
                )
            )
        )
        emConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
        emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            emConfig
        )
        dbCpkStorage = DBCpkStorage(emFactory)
    }

    @BeforeEach
    fun cleanDb() {
        setOf(DUMMY_HASH_1, DUMMY_HASH_2, DUMMY_HASH_3).forEach {
            removeCpkDataEntity(it, emFactory)
        }
    }

    @Test
    fun `gets cpk ids not in`() {
        val checksums = setOf(DUMMY_HASH_1, DUMMY_HASH_2, DUMMY_HASH_3)

        checksums.forEach { storeCpkDataEntity(it, byteArrayOf(0x01, 0x02, 0x03), emFactory) }

        val cpkIds = dbCpkStorage.getCpkIdsNotIn(setOf(DUMMY_HASH_3))
        assertEquals(checksums - DUMMY_HASH_3, cpkIds)
    }

    @Test
    fun `on gets cpk ids not in empty set, returns full set`() {
        val checksums = setOf(DUMMY_HASH_1, DUMMY_HASH_2, DUMMY_HASH_3)

        checksums.forEach { storeCpkDataEntity(it, byteArrayOf(0x01, 0x02, 0x03), emFactory) }

        val cpkIds = dbCpkStorage.getCpkIdsNotIn(setOf())
        assertEquals(checksums, cpkIds)
    }

    @Test
    fun `gets cpk blob by cpk id`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        storeCpkDataEntity(DUMMY_HASH_1, bytes, emFactory)
        val cpkChecksumData = dbCpkStorage.getCpkDataByCpkId(DUMMY_HASH_1)
        assertTrue(bytes.contentEquals(cpkChecksumData.data))
        assertEquals(DUMMY_HASH_1, cpkChecksumData.checksum)
    }
}