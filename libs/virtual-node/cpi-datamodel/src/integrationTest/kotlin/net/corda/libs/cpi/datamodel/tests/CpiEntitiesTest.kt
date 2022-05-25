package net.corda.libs.cpi.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpkCordappManifest
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.CpkDataEntity
import net.corda.libs.cpi.datamodel.CpkDependency
import net.corda.libs.cpi.datamodel.CpkFormatVersion
import net.corda.libs.cpi.datamodel.CpkManifest
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import net.corda.libs.cpi.datamodel.CpkMetadataEntityKey
import net.corda.libs.cpi.datamodel.ManifestCorDappInfo
import net.corda.libs.cpi.datamodel.findAllCpiMetadata
import net.corda.libs.cpi.datamodel.findCpkChecksumsNotIn
import net.corda.libs.cpi.datamodel.findCpkDataEntity
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import javax.persistence.EntityManagerFactory
import kotlin.streams.toList

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpiEntitiesIntegrationTest {
    private val dbConfig: EntityManagerConfiguration

    init {
        // comment this out if you want to run the test against a real postgres
        //  NOTE: the blob storage doesn't work in HSQL, hence skipping the majority of the test.
        // System.setProperty("postgresPort", "5432")
        dbConfig = DbUtils.getEntityManagerConfiguration("cpi_db")

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
        val cpkMetadataEntity =
            CpkMetadataEntityFactory.create(
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
                it.merge(cpkMetadataEntity)
                it.flush()
            }
        }

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use {
            val loadedCpkMetadataEntity = it.find(
                CpkMetadataEntity::class.java,
                CpkMetadataEntityKey(cpi, cpkMetadataEntity.cpkFileChecksum)
            )
            val loadedCpkDataEntity = it.find(CpkDataEntity::class.java, cpk.fileChecksum)

            assertThat(loadedCpkMetadataEntity).isEqualTo(cpkMetadataEntity)
            assertThat(loadedCpkMetadataEntity.cpi).isEqualTo(cpi)
            assertThat(loadedCpkDataEntity).isEqualTo(cpk)
        }
    }

    @Test
    fun `can add cpk to cpi`() {
        // HSQL doesn't support the blob storage, so skipping from here.
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

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
        val cpk = CpkDataEntity(
            "cpk-checksum-$cpkId",
            ByteArray(2000),
        )
        val cpkMetadataEntity =
            CpkMetadataEntityFactory.create(
                cpi,
                cpk.fileChecksum,
                "test-cpk.cpk",
            )

        emFactory.use { em ->
            em.transaction {
                // saving cpk should also save related cpi
                it.persist(cpk)
                it.persist(cpkMetadataEntity)
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
        val cpkMetadataEntity2 = CpkMetadataEntityFactory.create(
            cpi,
            cpk2.fileChecksum,
            "test-cpk2.cpk"
        )

        emFactory.use { em ->
            em.transaction {
                it.merge(cpkMetadataEntity2)
                it.flush()
            }
        }

        val loadedCpi = emFactory.use {
            it.find(CpiMetadataEntity::class.java,
                CpiMetadataEntityKey(cpi.name, cpi.version, cpi.signerSummaryHash))
        }

        // Check cpks meta is eagerly loaded
        assertThat(loadedCpi.cpks).containsExactlyInAnyOrder(
            cpkMetadataEntity, cpkMetadataEntity2
        )
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

        assertEquals(cpkChecksums[0], fetchedCpkChecksums[0])
        assertEquals(cpkChecksums[1], fetchedCpkChecksums[1])
        assertEquals(cpkChecksums[2], fetchedCpkChecksums[2])
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

        assertEquals(cpkChecksums[2], fetchedCpkChecksums[0])
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

        insertCpkChecksums(cpkChecksums, emFactory)

        val cpkDataEntity =
            emFactory.transaction {
                it.findCpkDataEntity(cpkChecksums[0])
            }

        val expectedCpkDataEntity = CpkDataEntity(
            "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA",
            byteArrayOf(0x01, 0x02, 0x03)
        )
        assertEquals(expectedCpkDataEntity, cpkDataEntity)
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
                CpkMetadataEntityFactory.create(cpi, cpk.fileChecksum, "test-cpk.cpk")

            emFactory.use { em ->
                em.transaction {
                    it.persist(cpk)
                    // saving cpk metadata should also save related cpi
                    it.persist(cpkMetadataEntity)
                    it.flush()
                }
            }
        }

        // Find all - fetches eagerly. We need to see
        val cpisEagerlyLoaded = emFactory.use { em ->
            em.transaction {
                // Not working as expected. The below produces one select query for fetching the CPIs
                // and one select per CPK fetch. Needs fixing as per https://r3-cev.atlassian.net/browse/CORE-4864
                em.findAllCpiMetadata().toList() // toList for EAGER fetching
            }
        }

        cpisEagerlyLoaded.forEach {
            it.cpks.forEach { cpkMetadataEntity ->
                assertThat(cpkMetadataEntity.cpkDependencies.count()).isEqualTo(2)
                assertThat(cpkMetadataEntity.cpkCordappManifest).isNotNull
                assertThat(cpkMetadataEntity.cpkCordappManifest?.workflowInfo).isNotNull
                assertThat(cpkMetadataEntity.cpkCordappManifest?.contractInfo).isNotNull
            }
        }

        // Find all - fetch lazy - stream through DB data
        emFactory.use { em ->
            em.transaction {
                // Not working as expected. The below produces one select query for fetching the CPIs
                // and one select per CPK fetch. Needs fixing as per https://r3-cev.atlassian.net/browse/CORE-4864.
                // It should rather create the result set once and then stream that into memory.
                val cpisLazyLoaded = em.findAllCpiMetadata()

                cpisLazyLoaded.forEach {
                    it.cpks.forEach { cpkMetadataEntity ->
                        assertThat(cpkMetadataEntity.cpkDependencies.count()).isEqualTo(2)
                        assertThat(cpkMetadataEntity.cpkCordappManifest).isNotNull
                        assertThat(cpkMetadataEntity.cpkCordappManifest?.workflowInfo).isNotNull
                        assertThat(cpkMetadataEntity.cpkCordappManifest?.contractInfo).isNotNull
                    }
                }
            }
        }
    }

    // Embeddable references get null when all Embedded class' properties are null
    @Test
    fun `CpkCordappManifest hashCode works when populated with null embedded object ManifestCorDappInfo`() {
        cleanUpDb()
        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        val cpiId = UUID.randomUUID()
        val cpi = CpiMetadataEntityFactory.create(cpiId)
        val cpkId = UUID.randomUUID()
        val cpk = CpkDataEntityFactory.create(cpkId)
        val cpkMetadataEntity =
            CpkMetadataEntityFactory.create(
                cpi,
                cpk.fileChecksum,
                "test-cpk.cpk",
                ManifestCorDappInfo(null, null, null, null)
            )

        emFactory.use { em ->
            em.transaction {
                it.persist(cpk)
                // saving cpk metadata should also save related cpi
                it.persist(cpkMetadataEntity)
                it.flush()
            }
        }

        val primaryKey = CpiMetadataEntityKey(
            cpi.name,
            cpi.version,
            cpi.signerSummaryHash
        )

        emFactory.use { em ->
            em.find(CpiMetadataEntity::class.java, primaryKey)
        }
    }

    private fun cleanUpDb() {
        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        emFactory.transaction {
            it.createNativeQuery("DELETE FROM config.cpk_cordapp_manifest").executeUpdate()

            it.createNativeQuery("DELETE FROM config.cpk_dependency").executeUpdate()

            it.createNativeQuery("DELETE FROM config.cpk_library").executeUpdate()

            it.createQuery("DELETE FROM ${CpkMetadataEntity::class.simpleName}").executeUpdate()

            it.createQuery("DELETE FROM ${CpkDataEntity::class.simpleName}").executeUpdate()
        }
    }

    private fun insertCpkChecksums(cpkChecksums: List<String>, emFactory: EntityManagerFactory) {
        emFactory.transaction {
            cpkChecksums.forEach { cpkChecksum ->
                it.persist(
                    CpkDataEntity(
                        cpkChecksum,
                        byteArrayOf(0x01, 0x02, 0x03)
                    )
                )
            }
        }
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

private object CpkMetadataEntityFactory {
    fun create(
        cpiMetadataEntity: CpiMetadataEntity,
        cpkFileChecksum: String,
        cpkFileName: String,
        contractInfo: ManifestCorDappInfo = ManifestCorDappInfo("app-short-name", "app-vendor", 1, "licence")
    ) = CpkMetadataEntity(
        cpiMetadataEntity,
        cpkFileChecksum,
        cpkFileName,
        "main-bundle-name",
        "main-bundle-version",
        "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA",
        CpkManifest(CpkFormatVersion(2, 1)),
        "cpk-main-bundle",
        null,
        setOf(
            "lib1",
            "lib2"
        ),
        cpkDependencies = setOf(
            CpkDependency(
                "main-bundle-name-1",
                "main-bundle-version-1",
                "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA"
            ),
            CpkDependency(
                "main-bundle-name-2",
                "main-bundle-version-2",
                "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FB"
            )
        ),
        cpkCordappManifest = CpkCordappManifest(
            "",
            "",
            1,
            2,
            contractInfo,
            ManifestCorDappInfo("short-name", "vendor", 1, "license")
        )
    )
}