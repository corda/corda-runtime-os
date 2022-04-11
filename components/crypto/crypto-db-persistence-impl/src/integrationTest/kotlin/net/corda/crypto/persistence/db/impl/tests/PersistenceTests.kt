package net.corda.crypto.persistence.db.impl.tests

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.persistence.SoftCryptoKeyCacheProvider
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityPrimaryKey
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.data.config.Configuration
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.LoggingUtils.emphasise
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.io.StringWriter
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import kotlin.random.Random

@ExtendWith(ServiceExtension::class)
class PersistenceTests {
    companion object {
        private val logger = contextLogger()

        private val CLIENT_ID = "${PersistenceTests::class.java}-integration-test"

        private const val CRYPTO_CONFIGURATION_VALUE: String = "{}"

        private const val MESSAGING_CONFIGURATION_VALUE: String = """
            componentVersion="5.1"
            subscription {
                consumer {
                    close.timeout = 6000
                    poll.timeout = 6000
                    thread.stop.timeout = 6000
                    processor.retries = 3
                    subscribe.retries = 3
                    commit.retries = 3
                }
                producer {
                    close.timeout = 6000
                }
            }
      """

        private const val BOOT_CONFIGURATION_VALUE = """
        instanceId=1
    """

        @InjectService(timeout = 5000)
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService(timeout = 5000)
        lateinit var lbm: LiquibaseSchemaMigrator

        @InjectService(timeout = 5000)
        lateinit var coordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 5000L)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 5000)
        lateinit var cipherSchemeMetadata: CipherSchemeMetadata

        @InjectService(timeout = 5000)
        lateinit var softCryptoCacheProvider: SoftCryptoKeyCacheProvider

        @InjectService(timeout = 5000)
        lateinit var configurationReadService: ConfigurationReadService

        @InjectService(timeout = 5000)
        lateinit var dbConnectionManager: DbConnectionManager

        //@InjectService(timeout = 5000)
        //lateinit var dbAdmin: DbAdmin

        @InjectService(timeout = 5000)
        lateinit var entitiesRegistry: JpaEntitiesRegistry

        private lateinit var dependentComponents: DependentComponents

        private lateinit var coordinator: LifecycleCoordinator

        private lateinit var configEmf: EntityManagerFactory

        private lateinit var cryptoEmf: EntityManagerFactory

        private val configFactory = SmartConfigFactory.create(
            ConfigFactory.parseString(
                """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=key
            ${SmartConfigFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            )
        )

        private val config = configFactory.create(DbUtils.createConfig("configuration_db"))

        private val cryptoDbConfig: EntityManagerConfiguration =
            DbUtils.getEntityManagerConfiguration("crypto")

        private val configDbConfig: EntityManagerConfiguration =
            DbUtils.getEntityManagerConfiguration("configuration_db")

        @JvmStatic
        @BeforeAll
        fun setup() {
            //setupConfigDb()
            setupCryptoEntities()
            //setupDependencies()
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            if (this::cryptoEmf.isInitialized) {
                cryptoEmf.close()
            }
            if(this::configEmf.isInitialized) {
                configEmf.close()
            }
        }

        private fun setupConfigDb() {
            val cl = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        DbSchema::class.java.packageName,
                        listOf("net/corda/db/schema/config/db.changelog-master.xml"),
                        DbSchema::class.java.classLoader
                    )
                )
            )
            configDbConfig.dataSource.connection.use { connection ->
                LiquibaseSchemaMigratorImpl().updateDb(connection, cl)
            }
            configEmf = entityManagerFactoryFactory.create(
                "DB Admin integration test",
                ConfigurationEntities.classes.toList(),
                configDbConfig
            )
        }

        private fun setupCryptoEntities() {
            val schemaClass = DbSchema::class.java
            val bundle = FrameworkUtil.getBundle(schemaClass)
            logger.info("Crypto schema bundle $bundle".emphasise())
            logger.info("Create Schema for ${cryptoDbConfig.dataSource.connection.metaData.url}".emphasise())
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
                lbm.createUpdateSql(cryptoDbConfig.dataSource.connection, cl, it)
                logger.info("Schema creation SQL: $it")
            }
            lbm.updateDb(cryptoDbConfig.dataSource.connection, cl)
            logger.info("Create Entities".emphasise())
            cryptoEmf = entityManagerFactoryFactory.create(
                CordaDb.Crypto.persistenceUnitName,
                CryptoEntities.classes.toList(),
                cryptoDbConfig
            )
        }

        private fun setupDependencies() {
            with(publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))) {
                start()
                publish(
                    listOf(
                        Record(
                            Schemas.Config.CONFIG_TOPIC,
                            ConfigKeys.MESSAGING_CONFIG,
                            Configuration(MESSAGING_CONFIGURATION_VALUE, "1")
                        ),
                        Record(
                            Schemas.Config.CONFIG_TOPIC,
                            ConfigKeys.CRYPTO_CONFIG,
                            Configuration(CRYPTO_CONFIGURATION_VALUE, "1")
                        )
                    )
                )
            }
            dependentComponents = DependentComponents.of(
                ::configurationReadService,
                ::dbConnectionManager,
                ::softCryptoCacheProvider
            )
            coordinator = coordinatorFactory.createCoordinator<PersistenceTests>(::eventHandler)
            finaliseCryptoDbSetup()
            coordinator.start()
            eventually {
                assertEquals(LifecycleStatus.UP, coordinator.status)
            }
        }

        private fun finaliseCryptoDbSetup() {
            entitiesRegistry.register(
                CordaDb.CordaCluster.persistenceUnitName,
                ConfigurationEntities.classes
            )
            entitiesRegistry.register(
                CordaDb.Crypto.persistenceUnitName,
                CryptoEntities.classes
            )
            dbConnectionManager.initialise(config)
            //dbAdmin.createDbAndUser(DbSchema.CRYPTO, "dml_user", "pwd_123", DbPrivilege.DML)
        }

        private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            logger.debug { "Test received event $event." }
            when (event) {
                is StartEvent -> {
                    dependentComponents.registerAndStartAll(coordinator)
                    configurationReadService.bootstrapConfig(
                        configFactory.create(ConfigFactory.parseString(BOOT_CONFIGURATION_VALUE))
                    )
                }
                is StopEvent -> {
                    dependentComponents.stopAll()
                }
                is RegistrationStatusChangeEvent -> {
                    logger.info("Test is ${event.status}")
                    coordinator.updateStatus(event.status)
                }
            }
        }
    }

    private class MockPublicKey(
        private val encoded: ByteArray
    ) : PublicKey {
        override fun getAlgorithm(): String = "MOCK"
        override fun getFormat(): String = "MOCK"
        override fun getEncoded(): ByteArray = encoded
    }

    @Test
    fun `Should persist and retrieve raw WrappingKeyEntity`() {
        val entity = WrappingKeyEntity(
            alias = UUID.randomUUID().toString(),
            created = Instant.now(),
            encodingVersion = 11,
            algorithmName = "AES",
            keyMaterial = Random(Instant.now().toEpochMilli()).nextBytes(512)
        )
        cryptoEmf.transaction { em ->
            em.persist(entity)
        }
        cryptoEmf.use { em ->
            val retrieved = em.find(WrappingKeyEntity::class.java, entity.alias)
            assertNotNull(retrieved)
            assertThat(retrieved).isEqualTo(entity)
            assertEquals(entity.alias, retrieved.alias)
            assertEquals(
                entity.created.epochSecond,
                retrieved.created.epochSecond
            )
            assertEquals(entity.encodingVersion, retrieved.encodingVersion)
            assertEquals(entity.algorithmName, retrieved.algorithmName)
            assertArrayEquals(entity.keyMaterial, retrieved.keyMaterial)
        }
    }

    @Test
    fun `Should persist and retrieve raw SigningKeyEntity`() {
        val random = Random(Instant.now().toEpochMilli())
        val publicKey = MockPublicKey(random.nextBytes(512))
        val tenantId = publicKeyIdOf(UUID.randomUUID().toString().toByteArray())
        val keyId = publicKeyIdOf(publicKey)
        val entity = SigningKeyEntity(
            tenantId = tenantId,
            keyId = keyId,
            created = Instant.now(),
            category = CryptoConsts.HsmCategories.LEDGER,
            schemeCodeName = RSA_CODE_NAME,
            publicKey = publicKey.encoded,
            keyMaterial = random.nextBytes(512),
            encodingVersion = 11,
            masterKeyAlias = UUID.randomUUID().toString(),
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            externalId = UUID.randomUUID().toString(),
        )
        cryptoEmf.transaction { em ->
            em.persist(entity)
        }
        cryptoEmf.use { em ->
            val retrieved = em.find(SigningKeyEntity::class.java, SigningKeyEntityPrimaryKey(
                tenantId = tenantId,
                keyId = keyId
            ))
            assertNotNull(retrieved)
            assertThat(retrieved).isEqualTo(entity)
            assertEquals(entity.tenantId, retrieved.tenantId)
            assertEquals(entity.keyId, retrieved.keyId)
            assertEquals(
                entity.created.epochSecond,
                retrieved.created.epochSecond
            )
            assertEquals(entity.category, retrieved.category)
            assertEquals(entity.schemeCodeName, retrieved.schemeCodeName)
            assertArrayEquals(entity.publicKey, retrieved.publicKey)
            assertArrayEquals(entity.keyMaterial, retrieved.keyMaterial)
            assertEquals(entity.encodingVersion, retrieved.encodingVersion)
            assertEquals(entity.masterKeyAlias, retrieved.masterKeyAlias)
            assertEquals(entity.alias, retrieved.alias)
            assertEquals(entity.hsmAlias, retrieved.hsmAlias)
            assertEquals(entity.externalId, retrieved.externalId)
        }
    }

    //@Test
    fun `Should be able to cache and then retrieve wrapping keys`() {
        val cache = softCryptoCacheProvider.getInstance(
            passphrase = "PASSPHRASE",
            salt = "SALT"
        )
        val newKey = WrappingKey.createWrappingKey(cipherSchemeMetadata)
        val alias = UUID.randomUUID().toString()
        cache.act {
            it.saveWrappingKey(alias, newKey, false)
            val cached = it.findWrappingKey(alias)
            assertNotNull(cached)
            assertEquals(newKey, cached)
        }
        cache.act {
            val cached = it.findWrappingKey(alias)
            assertNotNull(cached)
            assertEquals(newKey, cached)
        }
    }
}