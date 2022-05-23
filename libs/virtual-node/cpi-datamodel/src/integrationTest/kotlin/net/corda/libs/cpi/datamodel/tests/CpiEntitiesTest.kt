package net.corda.libs.cpi.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import net.corda.libs.cpi.datamodel.CpkDataEntity
import net.corda.libs.cpi.datamodel.CpkEntity
import net.corda.libs.cpi.datamodel.CpkEntityKey
import net.corda.libs.cpi.datamodel.findAllCpiMetadata
import net.corda.libs.cpi.datamodel.findCpkChecksumsNotIn
import net.corda.libs.cpi.datamodel.findCpkDataEntity
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import javax.persistence.EntityManagerFactory
import kotlin.streams.toList

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpiEntitiesIntegrationTest {
    private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("cpi_db")

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
            "file-upload-request-id-$cpiId",
            false
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
            val loadedCpi = em.find(
                CpiMetadataEntity::class.java,
                CpiMetadataEntityKey(cpi.name, cpi.version, cpi.signerSummaryHash)
            )


            assertThat(loadedCpi).isEqualTo(cpi)

            // CREATE a new CPK, data & entity
            val cpkId = UUID.randomUUID()
            val cpkData = CpkDataEntity(
                "cpk-checksum-$cpkId",
                ByteArray(2000),
            )
            val cpk =
                CpkEntityFactory.create(
                    loadedCpi,
                    cpkData.fileChecksum,
                    "test-cpk.cpk",
                    "test-cpk",
                    "1.2.3",
                    "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FB",
                )

            em.transaction {
                it.merge(cpkData)
                it.persist(cpk)
                it.flush()
            }

            EntityManagerFactoryFactoryImpl().create(
                "test_unit",
                CpiEntities.classes.toList(),
                dbConfig
            ).use {
                val loadedCpkEntity = it.find(
                    CpkEntity::class.java,
                    CpkEntityKey(cpi, cpk.metadata)
                )
                val loadedCpkDataEntity = it.find(CpkDataEntity::class.java, cpkData.fileChecksum)

                assertThat(loadedCpkEntity).isEqualTo(cpk)
                assertThat(loadedCpkEntity.cpi).isEqualTo(cpi)
                assertThat(loadedCpkDataEntity).isEqualTo(cpkData)
            }
        }
    }

    @Test
    fun `can add cpk to cpi`() {
        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

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
            "file-upload-request-id-$cpiId",
            false
        )
        val cpkId = UUID.randomUUID()
        val cpkData = CpkDataEntity(
            "cpk-checksum-$cpkId",
            ByteArray(2000),
        )
        val cpkEntity =
            CpkEntityFactory.create(
                cpi,
                cpkData.fileChecksum,
                "test-cpk.cpk",
                "test-cpk",
                "1.2.3",
                "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FB",
            )

        emFactory.use { em ->
            em.transaction {
                // saving cpk should also save related cpi
                it.persist(cpkData)
                it.persist(cpkEntity)
                it.flush()
            }
        }

        // Create another CPK
        val cpk2 = CpkDataEntity(
            "cpk-checksum-${UUID.randomUUID()}",
            ByteArray(2000),
        )

        emFactory.use { em ->
            em.transaction {
                it.persist(cpk2)
                it.flush()
            }
        }

        // add it to the existing CPI
        val expectedMeta = emFactory.use { em ->
            val loadedCpi = em.find(
                CpiMetadataEntity::class.java,
                CpiMetadataEntityKey(cpi.name, cpi.version, cpi.signerSummaryHash)
            )

            val cpkMetadataEntity2 = CpkEntityFactory.create(
                loadedCpi,
                cpk2.fileChecksum,
                "test-cpk2.cpk",
                "test-cpk2",
                "2.2.3",
                "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FB",
            )

            em.transaction {
                it.persist(cpkMetadataEntity2)
                it.flush()
            }
            cpkMetadataEntity2
        }

        val loadedCpi = emFactory.use {
            it.find(CpiMetadataEntity::class.java,
                CpiMetadataEntityKey(cpi.name, cpi.version, cpi.signerSummaryHash))
        }

        // assert the added one has been fetched eagerly.
        assertThat(loadedCpi.cpks).contains(expectedMeta)
    }

    @Test
    fun `on findCpkChecksumsNotIn an empty set returns all results`() {
        cleanUpDb()
        val cpkChecksums = listOf(
            "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA",
            "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FB",
            "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FC"
        )

        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        insertCpkChecksums(cpkChecksums, emFactory)

        val fetchedCpkChecksums =
        emFactory.transaction {
            it.findCpkChecksumsNotIn(emptyList())
        }

        assertThat(fetchedCpkChecksums).containsAll(cpkChecksums)
    }

    @Test
    fun `on findCpkChecksumsNotIn a checksum set returns all but this set`() {
        cleanUpDb()
        val cpkChecksums = listOf(
            "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA",
            "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FB",
            "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FC"
        )

        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        insertCpkChecksums(cpkChecksums, emFactory)

        val fetchedCpkChecksums =
            emFactory.transaction {
                it.findCpkChecksumsNotIn(listOf(
                    "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA",
                    "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FB"
                ))
            }

        assertThat(fetchedCpkChecksums).containsExactly(cpkChecksums[2])
    }

    @Test
    fun `finds CPK data entity`() {
        cleanUpDb()
        val cpkChecksums = listOf(
            "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA"
        )

        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        val insertedCpks = insertCpkChecksums(cpkChecksums, emFactory)

        val cpkDataEntity =
            emFactory.transaction {
                it.findCpkDataEntity(cpkChecksums[0])
            }

        assertThat(cpkDataEntity).isEqualTo(insertedCpks[0])
    }

    @Test
    fun `findAllCpiMetadata properly streams through DB data`() {
        cleanUpDb()
        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        // Create CPIs First
        for (i in 0..1) {
            val cpiId = UUID.randomUUID()
            val cpi = CpiMetadataEntityFactory.create(cpiId)
            val cpkId = UUID.randomUUID()
            val cpk = CpkDataEntityFactory.create(cpkId)
            val cpkMetadataEntity =
                CpkEntityFactory.create(
                    cpi, cpk.fileChecksum,
                    "test-cpk.cpk$i",
                    "test-cpk$i",
                    "$i.2.3",
                    "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FB",
                )

            emFactory.use { em ->
                em.transaction {
                    it.persist(cpk)
                    // saving cpk metadata should also save related cpi
                    it.persist(cpkMetadataEntity)
                    it.flush()
                }
            }
        }

        // Find all - fetches eagerly
        val cpisEagerlyLoaded = emFactory.use { em ->
            em.transaction {
                em.findAllCpiMetadata().toList() // toList here materialises the stream.
            }
            // closing the EntityManager validates that we haven't returned proxies but instead eagerly loaded all data
        }

        cpisEagerlyLoaded.forEach {
            it.cpks.forEach { cpkMetadataEntity ->
                assertThat(cpkMetadataEntity.metadata).isNotNull
            }
        }

        // Repeat the above, but consume from stream
        emFactory.use { em ->
            em.transaction {
                val cpisLazyLoaded = em.findAllCpiMetadata()

                cpisLazyLoaded.forEach {
                    it.cpks.forEach { cpkMetadataEntity ->
                        assertThat(cpkMetadataEntity.metadata).isNotNull
                    }
                }
            }
        }
    }

    private fun cleanUpDb() {
        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        emFactory.transaction {
            it.createQuery("DELETE FROM ${CpkEntity::class.simpleName}").executeUpdate()
            it.createQuery("DELETE FROM ${CpkMetadataEntity::class.simpleName}").executeUpdate()
            it.createQuery("DELETE FROM ${CpkDataEntity::class.simpleName}").executeUpdate()
            it.createQuery("DELETE FROM ${CpiMetadataEntity::class.simpleName}").executeUpdate()
        }
    }

    private fun insertCpkChecksums(cpkChecksums: List<String>, emFactory: EntityManagerFactory):
            List<CpkDataEntity> {
        val cpkData = cpkChecksums.map { CpkDataEntity(it, byteArrayOf(0x01, 0x02, 0x03)) }
        emFactory.transaction { em ->
            cpkData.forEach {
                em.persist(it)
            }
        }
        return cpkData
    }
}

private object CpiMetadataEntityFactory {
    fun create(
        cpiId: UUID
    ) =
        CpiMetadataEntity(
            "test-cpi-$cpiId",
            "1.0",
            "test-cpi-hash",
            "test-cpi-$cpiId.cpi",
            "test-cpi.cpi-$cpiId-hash",
            "{group-policy-json}",
            "group-id",
            "file-upload-request-id-$cpiId",
            false
        )
}

private object CpkDataEntityFactory {
    fun create(cpkId: UUID) =
        CpkDataEntity(
            "cpk-checksum-$cpkId",
            ByteArray(2000),
        )
}

private object CpkEntityFactory {
    fun create(
        cpiMetadataEntity: CpiMetadataEntity,
        cpkFileChecksum: String,
        cpkFileName: String,
        name: String,
        version: String,
        signerSummaryHash: String,
    ) = CpkEntity(
        cpiMetadataEntity,
        CpkMetadataEntity(cpkFileChecksum, "1.0", "{}"),
        cpkFileName,
        name,
        version,
        signerSummaryHash
    )
}