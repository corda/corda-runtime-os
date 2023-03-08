package net.corda.libs.cpi.datamodel.entities.tests

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.parseSecureHash
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.libs.cpi.datamodel.entities.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.CpiCpkKey
import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.entities.findAllCpiMetadata
import net.corda.libs.cpi.datamodel.entities.internal.CpkFileEntity
import net.corda.libs.cpi.datamodel.repository.CpkFileRepositoryImpl
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import javax.persistence.EntityManagerFactory
import kotlin.streams.toList

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpiEntitiesIntegrationTest {
    private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("cpi_db")

    private val cpkFileRepository = CpkFileRepositoryImpl()

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
    fun `can persist cpi and cpks`() {
        val cpiId = UUID.randomUUID()
        val cpkId = UUID.randomUUID().toString()
        val cpkSignerSummaryHash = TestObject.genRandomChecksum().toString()

        val cpkData = CpkFileEntity(
            SecureHashImpl("SHA-256", "cpk-checksum-$cpkId".toByteArray()).toString(),
            ByteArray(2000),
        )
        val cpiCpk =
            TestObject.createCpiCpkEntity(
                "test-cpi-$cpiId", "1.0", TestObject.SIGNER_SUMMARY_HASH,
                cpkId, "1.2.3", cpkSignerSummaryHash,
                "test-cpk", cpkData.fileChecksum,
            )
        val cpi = TestObject.createCpi(cpiId, setOf(cpiCpk))

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.persist(cpi)
                it.persist(cpkData)
                it.flush()
            }
        }

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use {
            val loadedCpiEntity = it.find(
                CpiMetadataEntity::class.java,
                CpiMetadataEntityKey(cpi.name, cpi.version, cpi.signerSummaryHash)
            )

            assertThat(loadedCpiEntity.cpks).isEqualTo(cpi.cpks)
        }

    }

    @Test
    fun `can add cpk to cpi`() {
        val cpiId = UUID.randomUUID()
        val cpkId = UUID.randomUUID().toString()
        val cpkData = CpkFileEntity(
            SecureHashImpl("SHA-256", "cpk-checksum-$cpkId".toByteArray()).toString(),
            ByteArray(2000),
        )
        val cpiCpk =
            TestObject.createCpiCpkEntity(
                "test-cpi-$cpiId", "1.0", TestObject.SIGNER_SUMMARY_HASH,
                cpkId, "1.2.3", TestObject.genRandomChecksum().toString(),
                "test-cpk", cpkData.fileChecksum
            )
        val cpi = TestObject.createCpi(cpiId, setOf(cpiCpk))
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

        // Create another CPK
        val cpkName2 = "test-cpk2"
        val cpkVer2 = "2.2.3"
        val cpk2SignerSummaryHash = TestObject.genRandomChecksum().toString()
        val cpkData2 = CpkFileEntity(
            SecureHashImpl("SHA-256", "cpk-checksum-${UUID.randomUUID()}".toByteArray()).toString(),
            ByteArray(2000),
        )
        val cpkMetadataEntity2 = TestObject.createCpk(
            cpkData2.fileChecksum,
            cpkName2,
            cpkVer2,
            cpk2SignerSummaryHash,
        )

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            val loadedCpiEntity = em.find(
                CpiMetadataEntity::class.java,
                CpiMetadataEntityKey(cpi.name, cpi.version, cpi.signerSummaryHash)
            )

            val updated = loadedCpiEntity.copy(
                cpks = loadedCpiEntity.cpks.plus(
                    CpiCpkEntity(
                        CpiCpkKey(
                            cpi.name,
                            cpi.version,
                            cpi.signerSummaryHash,
                            cpkMetadataEntity2.cpkFileChecksum
                        ),
                        "test-cpk2.cpk",
                        cpkMetadataEntity2
                    )
                ),
            )

            em.transaction {
                it.merge(updated)
                it.persist(cpkData2)
                it.flush()
            }
        }

        val loadedCpi = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use {
            it.find(
                CpiMetadataEntity::class.java,
                CpiMetadataEntityKey(cpi.name, cpi.version, cpi.signerSummaryHash)
            )
        }

        // assert the added one has been fetched eagerly.
        assertThat(loadedCpi.cpks.singleOrNull {
            it.metadata.cpkFileChecksum == cpkMetadataEntity2.cpkFileChecksum
        }).isNotNull
    }

    @Test
    fun `can have second cpi with shared cpk`() {
        val cpiId = UUID.randomUUID()
        val cpkId = UUID.randomUUID().toString()
        val cpk2Id = UUID.randomUUID().toString()
        val cpkVer = "1.2.3"
        val cpkSignerSummaryHash = TestObject.genRandomChecksum().toString()
        val cpkData = CpkFileEntity(
            SecureHashImpl("SHA-256", "cpk-checksum-$cpkId".toByteArray()).toString(),
            ByteArray(2000),
        )
        val cpiCpk1 =
            TestObject.createCpiCpkEntity(
                "test-cpi-$cpiId", "1.0", TestObject.SIGNER_SUMMARY_HASH,
                cpkId, cpkVer, cpkSignerSummaryHash,
                "test-cpk1", cpkData.fileChecksum,
            )
        val cpi1 = TestObject.createCpi(cpiId, setOf(cpiCpk1))

        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.persist(cpi1)
                it.persist(cpkData)
                it.flush()
            }
        }

        val cpk2Ver = "2.2.3"
        val cpk2SignerSummaryHash = TestObject.genRandomChecksum().toString()
        // Create another CPK
        val cpkData2 = CpkFileEntity(
            SecureHashImpl("SHA-256", "cpk-checksum-${UUID.randomUUID()}".toByteArray()).toString(),
            ByteArray(2000),
        )
        val cpiCpk2 =
            TestObject.createCpiCpkEntity(
                "test-cpi-$cpiId", "1.0", TestObject.SIGNER_SUMMARY_HASH,
                cpk2Id, cpk2Ver, cpk2SignerSummaryHash,
                "test-cpk2", cpkData2.fileChecksum,
            )
        val cpi2 = TestObject.createCpi(cpiId, setOf(cpiCpk1, cpiCpk2))
        EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use { em ->
            em.transaction {
                it.merge(cpi2)
                it.persist(cpkData2)
                it.flush()
            }
        }

        val loadedCpi = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        ).use {
            it.find(
                CpiMetadataEntity::class.java,
                CpiMetadataEntityKey(cpi2.name, cpi2.version, cpi2.signerSummaryHash)
            )
        }

        assertThat(loadedCpi.cpks.size).isEqualTo(2)
    }

    @Test
    fun `on findCpkChecksumsNotIn an empty set returns all results`() {
        val cpkChecksums = List(3) { TestObject.genRandomChecksum() }

        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        insertCpkChecksums(cpkChecksums, emFactory)

        val fetchedCpkChecksums =
            emFactory.transaction {
                cpkFileRepository.findAll(it).map { it.fileChecksum }
            }

        assertThat(fetchedCpkChecksums).containsAll(cpkChecksums)
    }

    @Test
    fun `on findCpkChecksumsNotIn a checksum set returns all but this set`() {
        val cpkChecksums = List(3) { TestObject.genRandomChecksum() }

        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        insertCpkChecksums(cpkChecksums, emFactory)

        val fetchedCpkChecksums =
            emFactory.transaction {
                cpkFileRepository.findAll(it, fileChecksumsToExclude = cpkChecksums.take(2)).map { it.fileChecksum }
            }

        assertThat(fetchedCpkChecksums).contains(cpkChecksums[2])
    }

    @Test
    fun `finds CPK data entity`() {
        val cpkChecksums = listOf(
            TestObject.genRandomChecksum()
        )

        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        val insertedCpkFilesEntity = insertCpkChecksums(cpkChecksums, emFactory)

        val cpkFile =
            emFactory.transaction {
                cpkFileRepository.findById(it, cpkChecksums[0])
            }

        val expectedCpkFile = CpkFile(
            parseSecureHash(insertedCpkFilesEntity[0].fileChecksum),
            insertedCpkFilesEntity[0].data,
            insertedCpkFilesEntity[0].entityVersion
        )
        assertThat(cpkFile).isEqualTo(expectedCpkFile)
    }

    @Test
    fun `findAllCpiMetadata properly streams through DB data`() {
        val emFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            CpiEntities.classes.toList(),
            dbConfig
        )

        val cpiNames = mutableListOf<String>()
        // Create CPIs First
        for (i in 0..1) {
            val cpiId = UUID.randomUUID()
            val cpks = (1..2).associate {
                val cpkName = "test-cpk$it"
                val cpkVersion = "$i.2.3"
                val cpkSignerSummaryHash = TestObject.genRandomChecksum().toString()
                val cpkMetadataEntity =
                    TestObject.createCpiCpkEntity(
                        "test-cpi-$cpiId", "1.0", TestObject.SIGNER_SUMMARY_HASH,
                        cpkName, cpkVersion, cpkSignerSummaryHash,
                        "test-cpk.cpk$it", TestObject.genRandomChecksum().toString(),
                    )
                val cpkData = CpkFileEntity(
                    cpkMetadataEntity.id.cpkFileChecksum,
                    ByteArray(2000),
                )
                cpkMetadataEntity to cpkData
            }
            val cpi = TestObject.createCpi(cpiId, cpks.keys)
            cpiNames.add(cpi.name)

            emFactory.use { em ->
                em.transaction {
                    it.persist(cpi)
                    cpks.values.forEach { cpkData ->
                        it.persist(cpkData)
                    }
                    it.flush()
                }
            }
        }

        // Find all - fetches eagerly
        println("**** [START] findAllCpiMetadata query as list ****")
        val cpisEagerlyLoaded = emFactory.use { em ->
            em.transaction {
                em.findAllCpiMetadata().toList() // toList here materialises the stream.
            }
            // closing the EntityManager validates that we haven't returned proxies but instead eagerly loaded all data
        }

        cpisEagerlyLoaded.filter {
            it.name in cpiNames
        }.forEach {
            assertThat(it.cpks.size).isEqualTo(2)
            it.cpks.forEach { cpkMetadataEntity ->
                println("****       invoke metadata property ****")
                assertThat(cpkMetadataEntity.metadata).isNotNull
            }
        }
        println("**** [END] findAllCpiMetadata query as list ****")

        // Repeat the above, but consume from stream
        println("**** [START] findAllCpiMetadata query as stream ****")
        emFactory.use { em ->
            em.transaction {
                val cpisLazyLoaded = em.findAllCpiMetadata()

                cpisLazyLoaded.filter {
                    it.name in cpiNames
                }.forEach {
                    assertThat(it.cpks.size).isEqualTo(2)
                    it.cpks.forEach { cpkMetadataEntity ->
                        println("****       invoke metadata property ****")
                        assertThat(cpkMetadataEntity.metadata).isNotNull
                    }
                }
            }
        }
        println("**** [END] findAllCpiMetadata query as stream ****")
    }

    private fun insertCpkChecksums(cpkChecksums: List<SecureHash>, emFactory: EntityManagerFactory):
            List<CpkFileEntity> {
        val cpkFileEntity = mutableListOf<CpkFileEntity>()
        emFactory.transaction { em ->
            cpkChecksums.forEach {
                val cpkName = "file.cpk"
                val cpkVer = "1.2.3"
                val cpkSignerSummaryHash = TestObject.genRandomChecksum().toString()

                val cpkMeta = TestObject.createCpk(it.toString(), cpkName, cpkVer, cpkSignerSummaryHash)
                val cpkData = CpkFileEntity(
                    it.toString(),
                    byteArrayOf(0x01, 0x02, 0x03)
                )

                em.persist(cpkMeta)
                em.persist(cpkData)
                cpkFileEntity.add(cpkData)
            }
        }
        return cpkFileEntity
    }
}
