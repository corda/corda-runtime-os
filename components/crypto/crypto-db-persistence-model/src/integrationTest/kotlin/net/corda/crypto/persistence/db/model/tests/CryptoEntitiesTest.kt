package net.corda.crypto.persistence.db.model.tests

import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.test.util.LoggingUtils.emphasise
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.io.StringWriter
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import kotlin.random.Random

@ExtendWith(ServiceExtension::class)
class CryptoEntitiesTest {
    companion object {
        private val logger = contextLogger()

        @InjectService
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService
        lateinit var lbm: LiquibaseSchemaMigrator

        lateinit var emf: EntityManagerFactory

        private val dbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration("crypto")

        @JvmStatic
        @BeforeAll
        fun setupEntities() {
            val schemaClass = DbSchema::class.java
            val bundle = FrameworkUtil.getBundle(schemaClass)
            logger.info("Crypto schema bundle $bundle".emphasise())

            logger.info("Create Schema for ${dbConfig.dataSource.connection.metaData.url}".emphasise())
            val fullName = schemaClass.packageName + ".crypto"
            val resourcePrefix = fullName.replace('.', '/')
            val cl = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        fullName,
                        listOf("$resourcePrefix/db.changelog-master.xml"),
                        classLoader = schemaClass.classLoader
                    )
                )
            )
            StringWriter().use {
                lbm.createUpdateSql(dbConfig.dataSource.connection, cl, it)
                logger.info("Schema creation SQL: $it")
            }
            lbm.updateDb(dbConfig.dataSource.connection, cl)

            logger.info("Create Entities".emphasise())

            emf = entityManagerFactoryFactory.create(
                CordaDb.Crypto.persistenceUnitName,
                CryptoEntities.classes.toList(),
                dbConfig
            )
        }

        @AfterAll
        @JvmStatic
        fun done() {
            if (this::emf.isInitialized) {
                emf.close()
            }
        }
    }

    @Test
    fun `Should persist and retrieve crypto entities`() {
        val wrappingKey = WrappingKeyEntity(
            alias = UUID.randomUUID().toString(),
            created = Instant.now(),
            encodingVersion = 11,
            algorithmName = "AES",
            keyMaterial = Random(Instant.now().toEpochMilli()).nextBytes(512)
        )
        emf.transaction { em ->
            em.persist(wrappingKey)
        }
        emf.use { em ->
            val retrieved = em.createQuery(
                "from WrappingKeyEntity where alias = '${wrappingKey.alias}'",
                wrappingKey.javaClass
            ).singleResult
            assertThat(retrieved).isEqualTo(wrappingKey)
            assertEquals(wrappingKey.alias, retrieved.alias)
            assertEquals(wrappingKey.created.epochSecond, retrieved.created.epochSecond)
            assertEquals(wrappingKey.encodingVersion, retrieved.encodingVersion)
            assertEquals(wrappingKey.algorithmName, retrieved.algorithmName)
            assertArrayEquals(wrappingKey.keyMaterial, retrieved.keyMaterial)
        }
    }
}