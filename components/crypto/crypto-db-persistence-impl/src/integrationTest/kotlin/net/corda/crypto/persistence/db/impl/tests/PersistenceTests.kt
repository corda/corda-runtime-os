package net.corda.crypto.persistence.db.impl.tests

import com.typesafe.config.ConfigFactory
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_SERVICE_NAME
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.EXTERNAL_ID_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.impl.config.createDefaultCryptoConfig
import net.corda.crypto.impl.config.hsmPersistence
import net.corda.crypto.impl.config.signingPersistence
import net.corda.crypto.impl.config.softPersistence
import net.corda.crypto.persistence.HSMTenantAssociation
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.db.impl.hsm.HSMCacheImpl
import net.corda.crypto.persistence.db.impl.signing.SigningKeyCacheImpl
import net.corda.crypto.persistence.db.impl.soft.SoftCryptoKeyCacheImpl
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryMapEntity
import net.corda.crypto.persistence.db.model.HSMConfigEntity
import net.corda.crypto.persistence.db.model.MasterKeyPolicy
import net.corda.crypto.persistence.db.model.PrivateKeyPolicy
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityPrimaryKey
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.DbUtils
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.base.util.toHex
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.crypto.publicKeyId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.PersistenceException
import javax.sql.DataSource

@ExtendWith(ServiceExtension::class)
class PersistenceTests {
    companion object {
        @InjectService(timeout = 5000)
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService(timeout = 5000)
        lateinit var lbm: LiquibaseSchemaMigrator

        @InjectService(timeout = 5000)
        lateinit var entitiesRegistry: JpaEntitiesRegistry

        @InjectService(timeout = 5000)
        lateinit var layeredPropertyMapFactory: LayeredPropertyMapFactory

        @InjectService(timeout = 5000)
        lateinit var schemeMetadata: CipherSchemeMetadata

        private lateinit var databaseInstaller: DatabaseInstaller

        private val configFactory = SmartConfigFactory.create(
            ConfigFactory.parseString(
                """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=key
            ${SmartConfigFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            )
        )

        private lateinit var cryptoEmf: EntityManagerFactory

        private val cryptoDbConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration(
            inMemoryDbName = "crypto",
            schemaName = DbSchema.CRYPTO
        )

        @JvmStatic
        @BeforeAll
        fun setup() {
            databaseInstaller = DatabaseInstaller(
                entityManagerFactoryFactory,
                lbm,
                entitiesRegistry
            )
            cryptoEmf = databaseInstaller.setupDatabase(
                cryptoDbConfig,
                "crypto",
                CordaDb.Crypto.persistenceUnitName,
                CryptoEntities.classes,
                DbSchema.CRYPTO
            )
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            if (::cryptoEmf.isInitialized) {
                cryptoEmf.close()
            }
        }

        private fun randomTenantId() = publicKeyIdFromBytes(UUID.randomUUID().toString().toByteArray())

        fun generateKeyPair(signatureSchemeName: String): KeyPair {
            val scheme = schemeMetadata.findSignatureScheme(signatureSchemeName)
            val keyPairGenerator = KeyPairGenerator.getInstance(
                scheme.algorithmName,
                schemeMetadata.providers.getValue(scheme.providerName)
            )
            if (scheme.algSpec != null) {
                keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
            } else if (scheme.keySize != null) {
                keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
            }
            return keyPairGenerator.generateKeyPair()
        }
    }

    private fun createAndPersistHSMEntities(
        tenantId: String,
        category: String,
        masterKeyPolicy: MasterKeyPolicy = MasterKeyPolicy.SHARED,
        capacity: Int = 3,
        categories: (HSMConfigEntity) -> List<HSMCategoryMapEntity> = { emptyList() }
    ): HSMCategoryAssociationEntity {
        val configId = UUID.randomUUID().toString()
        val associationId = UUID.randomUUID().toString()
        val categoryAssociationId = UUID.randomUUID().toString()
        val config = createAndPersistHSMConfigEntity(
            configId = configId,
            masterKeyPolicy = masterKeyPolicy,
            capacity = capacity,
            categories = categories
        )
        val association = HSMAssociationEntity(
            id = associationId,
            tenantId = tenantId,
            config = config,
            timestamp = Instant.now(),
            masterKeyAlias = if (masterKeyPolicy == MasterKeyPolicy.NEW) {
                UUID.randomUUID().toString().toByteArray().toHex().take(30)
            } else {
                null
            },
            aliasSecret = "Hello World!".toByteArray()
        )
        cryptoEmf.transaction { em ->
            em.persist(association)
        }
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = categoryAssociationId,
            category = category,
            hsm = association,
            timestamp = Instant.now()
        )
        cryptoEmf.transaction { em ->
            em.persist(categoryAssociation)
        }
        return categoryAssociation
    }

    private fun createAndPersistHSMConfigEntity(
        configId: String,
        serviceName: String = "test",
        masterKeyPolicy: MasterKeyPolicy = MasterKeyPolicy.SHARED,
        capacity: Int = 3,
        categories: (HSMConfigEntity) -> List<HSMCategoryMapEntity> = { emptyList() }
    ): HSMConfigEntity {
        val config = createHSMConfigEntity(
            configId = configId,
            serviceName = serviceName,
            masterKeyPolicy = masterKeyPolicy,
            capacity = capacity
        )
        cryptoEmf.transaction { em ->
            em.persist(config)
            categories(config).forEach { c -> em.persist(c) }
        }
        return config
    }

    private fun createHSMConfigEntity(
        configId: String,
        serviceName: String = "test",
        masterKeyPolicy: MasterKeyPolicy = MasterKeyPolicy.SHARED,
        capacity: Int = 3
    ) = HSMConfigEntity(
        id = configId,
        timestamp = Instant.now(),
        workerLabel = UUID.randomUUID().toString(),
        description = "Test configuration",
        masterKeyPolicy = masterKeyPolicy,
        masterKeyAlias = if (masterKeyPolicy == MasterKeyPolicy.SHARED) "some-alias" else null,
        supportedSchemes = "CORDA.RSA,CORDA.ECDSA.SECP256K1,CORDA.ECDSA.SECP256R1,CORDA.EDDSA.ED25519",
        retries = 0,
        timeoutMills = 5000,
        serviceName = serviceName,
        serviceConfig = "{}".toByteArray(),
        capacity = capacity
    )

    private fun createHSMInfo(
        configId: String,
        serviceName: String = "test",
        masterKeyPolicy: net.corda.data.crypto.wire.hsm.MasterKeyPolicy =
            net.corda.data.crypto.wire.hsm.MasterKeyPolicy.SHARED,
        capacity: Int = 3
    ) = HSMInfo(
        configId,
        Instant.now(),
        UUID.randomUUID().toString(),
        "Test configuration",
        masterKeyPolicy,
        if (masterKeyPolicy == net.corda.data.crypto.wire.hsm.MasterKeyPolicy.SHARED) "some-alias" else null,
        0,
        5000,
        listOf(
            "CORDA.RSA", "CORDA.ECDSA.SECP256K1", "CORDA.ECDSA.SECP256R1", "CORDA.EDDSA.ED25519"
        ),
        serviceName,
        capacity
    )

    private fun createHSMCacheImpl() = HSMCacheImpl(
        config = createDefaultCryptoConfig(KeyCredentials("salt", "passphrase")).hsmPersistence(),
        entityManagerFactory = cryptoEmf
    )

    private fun assertHSMCategoryAssociationEntity(
        expected: HSMCategoryAssociationEntity,
        actual: HSMTenantAssociation?
    ) {
        assertNotNull(actual)
        assertEquals(expected.category, actual!!.category)
        assertEquals(expected.hsm.config.id, actual.config.info.id)
        assertEquals(expected.hsm.tenantId, actual.tenantId)
        assertEquals(expected.hsm.masterKeyAlias, actual.masterKeyAlias)
        assertArrayEquals(expected.hsm.aliasSecret, actual.aliasSecret)
        assertHSMConfig(expected.hsm.config, actual.config)
    }

    private fun assertHSMConfig(expected: HSMConfigEntity, actual: HSMConfig?) {
        assertNotNull(actual)
        assertArrayEquals(expected.serviceConfig, actual!!.serviceConfig.array())
        assertHSMInfo(expected, actual.info)
    }

    private fun assertHSMInfo(expected: HSMConfigEntity, actual: HSMInfo?) {
        assertNotNull(actual)
        assertEquals(expected.id, actual!!.id)
        assertEquals(expected.masterKeyAlias, actual.masterKeyAlias)
        assertEquals(expected.workerLabel, actual.workerLabel)
        assertEquals(expected.description, actual.description)
        assertEquals(expected.retries, actual.retries)
        assertEquals(expected.timeoutMills, actual.timeoutMills)
        val expectedList = expected.supportedSchemes.split(",")
        assertTrue(actual.supportedSchemes.isNotEmpty())
        assertEquals(expectedList.size, actual.supportedSchemes.size)
        assertTrue(expectedList.all { actual.supportedSchemes.contains(it) })
        assertEquals(expected.serviceName, actual.serviceName)
        assertEquals(expected.masterKeyPolicy.name, actual.masterKeyPolicy.name)
        assertEquals(expected.capacity, actual.capacity)
    }

    private fun getHSMCategoryMapEntities(
        it: EntityManager,
        configId1: String
    ): List<HSMCategoryMapEntity> {
        val config = it.find(HSMConfigEntity::class.java, configId1)
        return it.createQuery(
            "SELECT m FROM HSMCategoryMapEntity m WHERE m.config=:config",
            HSMCategoryMapEntity::class.java
        )
            .setParameter("config", config)
            .resultList
    }

    @Test
    fun `Should persist and retrieve raw WrappingKeyEntity`() {
        val entity = WrappingKeyEntity(
            alias = UUID.randomUUID().toString(),
            created = Instant.now(),
            encodingVersion = 11,
            algorithmName = "AES",
            keyMaterial = generateKeyPair(EDDSA_ED25519_CODE_NAME).public.encoded
        )
        cryptoEmf.transaction { em ->
            em.persist(entity)
        }
        cryptoEmf.use { em ->
            val retrieved = em.find(WrappingKeyEntity::class.java, entity.alias)
            assertNotNull(retrieved)
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
        val keyPair = generateKeyPair(EDDSA_ED25519_CODE_NAME)
        val tenantId = randomTenantId()
        val keyId = keyPair.public.publicKeyId()
        val entity = SigningKeyEntity(
            tenantId = tenantId,
            keyId = keyId,
            created = Instant.now(),
            category = CryptoConsts.HsmCategories.LEDGER,
            schemeCodeName = RSA_CODE_NAME,
            publicKey = keyPair.public.encoded,
            keyMaterial = keyPair.private.encoded,
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
            val retrieved = em.find(
                SigningKeyEntity::class.java, SigningKeyEntityPrimaryKey(
                    tenantId = tenantId,
                    keyId = keyId
                )
            )
            assertNotNull(retrieved)
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

    @Test
    fun `Should persist and retrieve raw HSM related entities`() {
        val tenantId = randomTenantId()
        val configId = UUID.randomUUID().toString()
        val associationId = UUID.randomUUID().toString()
        val categoryAssociationId = UUID.randomUUID().toString()
        val categoryMappingId1 = UUID.randomUUID().toString()
        val categoryMappingId2 = UUID.randomUUID().toString()
        val config = createAndPersistHSMConfigEntity(configId = configId) {
            listOf(
                HSMCategoryMapEntity(
                    id = categoryMappingId1,
                    category = CryptoConsts.HsmCategories.LEDGER,
                    keyPolicy = PrivateKeyPolicy.WRAPPED,
                    timestamp = Instant.now(),
                    config = it
                ),
                HSMCategoryMapEntity(
                    id = categoryMappingId2,
                    category = CryptoConsts.HsmCategories.TLS,
                    keyPolicy = PrivateKeyPolicy.WRAPPED,
                    timestamp = Instant.now(),
                    config = it
                )
            )
        }
        val association = HSMAssociationEntity(
            id = associationId,
            tenantId = tenantId,
            config = config,
            timestamp = Instant.now(),
            masterKeyAlias = UUID.randomUUID().toString().toByteArray().toHex().take(30),
            aliasSecret = "Hello World!".toByteArray()
        )
        cryptoEmf.transaction { em ->
            em.persist(association)
        }
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = categoryAssociationId,
            category = CryptoConsts.HsmCategories.LEDGER,
            hsm = association,
            timestamp = Instant.now()
        )
        cryptoEmf.transaction { em ->
            em.persist(categoryAssociation)
        }
        cryptoEmf.use { em ->
            val retrieved = em.find(HSMCategoryAssociationEntity::class.java, categoryAssociationId)
            assertNotNull(retrieved)
            assertNotSame(categoryAssociation, retrieved)
            assertEquals(categoryAssociationId, retrieved.id)
            assertEquals(CryptoConsts.HsmCategories.LEDGER, retrieved.category)
            assertNotSame(association, retrieved.hsm)
            assertEquals(associationId, retrieved.hsm.id)
            assertEquals(tenantId, retrieved.hsm.tenantId)
            assertEquals(association.masterKeyAlias, retrieved.hsm.masterKeyAlias)
            assertArrayEquals(association.aliasSecret, retrieved.hsm.aliasSecret)
            assertNotSame(config, retrieved.hsm.config)
            assertEquals(configId, retrieved.hsm.config.id)
            assertEquals(config.masterKeyAlias, retrieved.hsm.config.masterKeyAlias)
            assertEquals(config.workerLabel, retrieved.hsm.config.workerLabel)
            assertEquals(config.description, retrieved.hsm.config.description)
            assertEquals(config.retries, retrieved.hsm.config.retries)
            assertEquals(config.timeoutMills, retrieved.hsm.config.timeoutMills)
            assertEquals(config.supportedSchemes, retrieved.hsm.config.supportedSchemes)
            assertEquals(config.serviceName, retrieved.hsm.config.serviceName)
            assertArrayEquals(config.serviceConfig, retrieved.hsm.config.serviceConfig)
            assertEquals(MasterKeyPolicy.SHARED, config.masterKeyPolicy)
            assertEquals(config.capacity, retrieved.hsm.config.capacity)

            val retrievedMapping1 = em.find(HSMCategoryMapEntity::class.java, categoryMappingId1)
            assertEquals(CryptoConsts.HsmCategories.LEDGER, retrievedMapping1.category)
            assertEquals(PrivateKeyPolicy.WRAPPED, retrievedMapping1.keyPolicy)
            assertEquals(configId, retrievedMapping1.config.id)

            val retrievedMapping2 = em.find(HSMCategoryMapEntity::class.java, categoryMappingId2)
            assertEquals(CryptoConsts.HsmCategories.TLS, retrievedMapping2.category)
            assertEquals(PrivateKeyPolicy.WRAPPED, retrievedMapping2.keyPolicy)
            assertEquals(configId, retrievedMapping2.config.id)
        }
    }

    @Test
    fun `Should fail to save HSMAssociationEntity with duplicate tenant and configuration`() {
        val tenantId = randomTenantId()
        val a1 = createAndPersistHSMEntities(tenantId, CryptoConsts.HsmCategories.LEDGER, MasterKeyPolicy.NEW)
        val association = HSMAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = a1.hsm.tenantId,
            config = a1.hsm.config,
            timestamp = Instant.now(),
            masterKeyAlias = UUID.randomUUID().toString().toByteArray().toHex().take(30),
            aliasSecret = "Hello World!".toByteArray()
        )
        assertThrows(PersistenceException::class.java) {
            cryptoEmf.transaction { em ->
                em.persist(association)
            }
        }
    }

    @Test
    fun `Should fail to save HSMCategoryAssociationEntity with duplicate category and association`() {
        val tenantId = randomTenantId()
        val a1 = createAndPersistHSMEntities(tenantId, CryptoConsts.HsmCategories.LEDGER, MasterKeyPolicy.NEW)
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            category = a1.category,
            hsm = a1.hsm,
            timestamp = Instant.now()
        )
        assertThrows(PersistenceException::class.java) {
            cryptoEmf.transaction { em ->
                em.persist(categoryAssociation)
            }
        }
    }

    @Test
    fun `findTenantAssociation should be able to find tenant HSM associations with categories`() {
        val tenantId1 = randomTenantId()
        val a1 = createAndPersistHSMEntities(tenantId1, CryptoConsts.HsmCategories.LEDGER, MasterKeyPolicy.NEW)
        val a2 = createAndPersistHSMEntities(tenantId1, CryptoConsts.HsmCategories.SESSION, MasterKeyPolicy.SHARED)
        val cache = createHSMCacheImpl()
        val r1 = cache.act { it.findTenantAssociation(tenantId1, CryptoConsts.HsmCategories.LEDGER) }
        val r2 = cache.act { it.findTenantAssociation(tenantId1, CryptoConsts.HsmCategories.SESSION) }
        assertHSMCategoryAssociationEntity(a1, r1)
        assertHSMCategoryAssociationEntity(a2, r2)
    }

    @Test
    fun `findTenantAssociation should return null when parameters are not matching`() {
        val tenantId = randomTenantId()
        createAndPersistHSMEntities(tenantId, CryptoConsts.HsmCategories.LEDGER, MasterKeyPolicy.NEW)
        val cache = createHSMCacheImpl()
        val r1 = cache.act { it.findTenantAssociation(tenantId, CryptoConsts.HsmCategories.SESSION) }
        assertNull(r1)
        val r2 = cache.act { it.findTenantAssociation(randomTenantId(), CryptoConsts.HsmCategories.LEDGER) }
        assertNull(r2)
    }

    @Test
    fun `findConfig should be able to find HSM config`() {
        val configId = UUID.randomUUID().toString()
        val expected = createAndPersistHSMConfigEntity(configId)
        val cache = createHSMCacheImpl()
        val actual = cache.act { it.findConfig(configId) }
        assertHSMConfig(expected, actual)
    }

    @Test
    fun `findConfig returns null when id is not matching`() {
        val configId = UUID.randomUUID().toString()
        createAndPersistHSMConfigEntity(configId)
        val cache = createHSMCacheImpl()
        val actual = cache.act { it.findConfig(UUID.randomUUID().toString()) }
        assertNull(actual)
    }

    @Test
    fun `lookup should be able to list HSM configs mathcing various filters`() {
        val configId1 = UUID.randomUUID().toString()
        val serviceName1 = UUID.randomUUID().toString()
        val configId2 = UUID.randomUUID().toString()
        val serviceName2 = UUID.randomUUID().toString()
        val configId3 = UUID.randomUUID().toString()
        val config1 = createAndPersistHSMConfigEntity(configId1, serviceName1)
        val config2 = createAndPersistHSMConfigEntity(configId2, serviceName2)
        val config3 = createAndPersistHSMConfigEntity(configId3, serviceName2)
        val cache = createHSMCacheImpl()
        val actual1 = cache.act { it.lookup(emptyMap()) }
        assertTrue(actual1.size >= 3) // there could be more left from other tests
        assertHSMInfo(config1, actual1.first { it.id == configId1 })
        assertHSMInfo(config2, actual1.first { it.id == configId2 })
        assertHSMInfo(config3, actual1.first { it.id == configId3 })
        val actual2 = cache.act {
            it.lookup(mapOf(CryptoConsts.HSMFilters.SERVICE_NAME_FILTER to UUID.randomUUID().toString()))
        }
        assertTrue(actual2.isEmpty())
        val actual3 = cache.act {
            it.lookup(mapOf(CryptoConsts.HSMFilters.SERVICE_NAME_FILTER to serviceName2))
        }
        assertTrue(actual3.size == 2)
        assertHSMInfo(config2, actual3.first { it.id == configId2 })
        assertHSMInfo(config3, actual3.first { it.id == configId3 })
        val actual4 = cache.act {
            it.lookup(mapOf(CryptoConsts.HSMFilters.SERVICE_NAME_FILTER to serviceName1))
        }
        assertTrue(actual4.size == 1)
        assertHSMInfo(config1, actual4.first { it.id == configId1 })
    }

    @Test
    fun `Should link categories to HSM and then assiciate and getHSMStats`() {
        val cache = createHSMCacheImpl()
        val configId1 = UUID.randomUUID().toString()
        val configId2 = UUID.randomUUID().toString()
        cache.act {
            it.add(
                createHSMInfo(configId = configId1, capacity = 5, serviceName = SOFT_HSM_SERVICE_NAME),
                "{}".toByteArray()
            )
        }
        cache.act {
            it.linkCategories(
                configId1, listOf(
                    HSMCategoryInfo(
                        CryptoConsts.HsmCategories.LEDGER,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
                    ),
                    HSMCategoryInfo(
                        CryptoConsts.HsmCategories.TLS,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.BOTH
                    )
                )
            )
        }
        cache.act {
            it.add(createHSMInfo(configId = configId2, capacity = 3), "{}".toByteArray())
        }
        cache.act {
            it.linkCategories(
                configId2, listOf(
                    HSMCategoryInfo(
                        CryptoConsts.HsmCategories.LEDGER,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
                    ),
                    HSMCategoryInfo(
                        CryptoConsts.HsmCategories.TLS,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
                    )
                )
            )
        }
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val tenantId3 = randomTenantId()
        val tenantId4 = randomTenantId()
        val tenantId5 = randomTenantId()
        cache.act {
            it.associate(tenantId1, CryptoConsts.HsmCategories.LEDGER, configId1)
            it.associate(tenantId2, CryptoConsts.HsmCategories.LEDGER, configId2)
            it.associate(tenantId3, CryptoConsts.HsmCategories.LEDGER, configId2)
            it.associate(tenantId4, CryptoConsts.HsmCategories.TLS, configId2)
            it.associate(tenantId5, CryptoConsts.HsmCategories.TLS, configId1)
        }
        val actual1 = cache.act { it.getHSMStats(CryptoConsts.HsmCategories.SESSION) }
        assertEquals(0, actual1.size)
        val actual2 = cache.act { it.getHSMStats(CryptoConsts.HsmCategories.LEDGER) }
        assertEquals(2, actual2.size)
        assertEquals(5, actual2.first { it.configId == configId1 }.capacity)
        assertEquals(2, actual2.first { it.configId == configId1 }.usages)
        assertEquals(3, actual2.first { it.configId == configId2 }.capacity)
        assertEquals(3, actual2.first { it.configId == configId2 }.usages)
        cache.act {
            it.associate(tenantId3, CryptoConsts.HsmCategories.TLS, configId2)
        }
        val actual3 = cache.act { it.getHSMStats(CryptoConsts.HsmCategories.LEDGER) }
        assertEquals(2, actual3.size)
        assertEquals(5, actual3.first { it.configId == configId1 }.capacity)
        assertEquals(2, actual3.first { it.configId == configId1 }.usages)
        assertEquals(3, actual3.first { it.configId == configId2 }.capacity)
        assertEquals(4, actual3.first { it.configId == configId2 }.usages)
    }

    @Test
    fun `linkCategories should replace previous mapping`() {
        val cache = createHSMCacheImpl()
        val configId = UUID.randomUUID().toString()
        cache.act {
            it.add(createHSMInfo(configId = configId, capacity = 5), "{}".toByteArray())
        }
        cache.act {
            it.linkCategories(
                configId, listOf(
                    HSMCategoryInfo(
                        CryptoConsts.HsmCategories.LEDGER,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
                    ),
                    HSMCategoryInfo(
                        CryptoConsts.HsmCategories.TLS,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.BOTH
                    )
                )
            )
        }
        val mapping1 = cryptoEmf.use {
            getHSMCategoryMapEntities(it, configId)
        }
        assertEquals(2, mapping1.size)
        assertTrue(mapping1.any {
            it.category == CryptoConsts.HsmCategories.LEDGER &&
                    it.keyPolicy == PrivateKeyPolicy.ALIASED
        })
        assertTrue(mapping1.any {
            it.category == CryptoConsts.HsmCategories.TLS &&
                    it.keyPolicy == PrivateKeyPolicy.BOTH
        })
        cache.act {
            it.linkCategories(
                configId, listOf(
                    HSMCategoryInfo(
                        CryptoConsts.HsmCategories.SESSION,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.WRAPPED
                    )
                )
            )
        }
        val mapping2 = cryptoEmf.use {
            getHSMCategoryMapEntities(it, configId)
        }
        assertEquals(1, mapping2.size)
        assertTrue(mapping2.any {
            it.category == CryptoConsts.HsmCategories.SESSION &&
                    it.keyPolicy == PrivateKeyPolicy.WRAPPED
        })
    }

    @Test
    fun `Should return linked categories`() {
        val cache = createHSMCacheImpl()
        val configId = UUID.randomUUID().toString()
        cache.act {
            it.add(createHSMInfo(configId = configId, capacity = 5), "{}".toByteArray())
        }
        cache.act {
            it.linkCategories(
                configId, listOf(
                    HSMCategoryInfo(
                        CryptoConsts.HsmCategories.LEDGER,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
                    ),
                    HSMCategoryInfo(
                        CryptoConsts.HsmCategories.TLS,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.BOTH
                    )
                )
            )
        }
        val links = cache.act {
            it.getLinkedCategories(configId)
        }
        assertEquals(2, links.size)
        assertTrue(links.any {
            it.category == CryptoConsts.HsmCategories.LEDGER &&
                    it.keyPolicy == net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
        })
        assertTrue(links.any {
            it.category == CryptoConsts.HsmCategories.TLS &&
                    it.keyPolicy == net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.BOTH
        })
    }

    @Test
    fun `Should merge HSM configuration`() {
        val cache = createHSMCacheImpl()
        val info = createHSMInfo(configId = "", capacity = 5)
        val configId = cache.act {
            it.add(info, "{}".toByteArray())
        }
        val config1 = cryptoEmf.use {
            it.find(HSMConfigEntity::class.java, configId)
        }
        info.id = configId // for the asser bellow to work
        assertHSMInfo(config1, info)
        val updated = HSMInfo(
            configId,
            Instant.now(),
            "new-worker-label",
            "New test configuration",
            net.corda.data.crypto.wire.hsm.MasterKeyPolicy.NONE,
            "new-master-alias",
            111,
            222,
            listOf(
                "something"
            ),
            "new-service-name",
            77
        )
        cache.act {
            it.merge(updated, "{}".toByteArray())
        }
        val config2 = cryptoEmf.use {
            it.find(HSMConfigEntity::class.java, updated.id)
        }
        assertEquals(configId, updated.id)
        assertHSMInfo(config2, updated)
    }

    @Test
    fun `Should be able to cache and then retrieve repeatedly wrapping keys`() {
        val cache = SoftCryptoKeyCacheImpl(
            config = createDefaultCryptoConfig(KeyCredentials("salt", "passphrase")).softPersistence(),
            entityManagerFactory = cryptoEmf,
            masterKey = WrappingKey.generateWrappingKey(schemeMetadata)
        )
        val alias1 = UUID.randomUUID().toString()
        val newKey1 = WrappingKey.generateWrappingKey(schemeMetadata)
        cache.act {
            it.saveWrappingKey(alias1, newKey1, false)
            val cached = it.findWrappingKey(alias1)
            assertNotNull(cached)
            assertEquals(newKey1, cached)
        }
        val alias2 = UUID.randomUUID().toString()
        val newKey2 = WrappingKey.generateWrappingKey(schemeMetadata)
        cache.act {
            it.saveWrappingKey(alias2, newKey2, false)
            val cached = it.findWrappingKey(alias2)
            assertNotNull(cached)
            assertEquals(newKey2, cached)
        }
        cache.act {
            val cached = it.findWrappingKey(alias1)
            assertNotNull(cached)
            assertEquals(newKey1, cached)
        }
        cache.act {
            val cached = it.findWrappingKey(alias1)
            assertNotNull(cached)
            assertEquals(newKey1, cached)
        }
    }

    @Test
    fun `Should fail to save wrapping key with same alias when failIfExists equals true`() {
        val cache = SoftCryptoKeyCacheImpl(
            config = createDefaultCryptoConfig(KeyCredentials("salt", "passphrase")).softPersistence(),
            entityManagerFactory = cryptoEmf,
            masterKey = WrappingKey.generateWrappingKey(schemeMetadata)
        )
        val alias = UUID.randomUUID().toString()
        val newKey1 = WrappingKey.generateWrappingKey(schemeMetadata)
        cache.act {
            it.saveWrappingKey(alias, newKey1, true)
            val cached = it.findWrappingKey(alias)
            assertNotNull(cached)
            assertEquals(newKey1, cached)
        }
        val newKey2 = WrappingKey.generateWrappingKey(schemeMetadata)
        assertThrows(PersistenceException::class.java) {
            cache.act {
                it.saveWrappingKey(alias, newKey2, true)
            }
        }
    }

    @Test
    fun `Should not override existing wrapping key with same alias when failIfExists equals false`() {
        val cache = SoftCryptoKeyCacheImpl(
            config = createDefaultCryptoConfig(KeyCredentials("salt", "passphrase")).softPersistence(),
            entityManagerFactory = cryptoEmf,
            masterKey = WrappingKey.generateWrappingKey(schemeMetadata)
        )
        val alias = UUID.randomUUID().toString()
        val newKey1 = WrappingKey.generateWrappingKey(schemeMetadata)
        cache.act {
            it.saveWrappingKey(alias, newKey1, false)
            val cached = it.findWrappingKey(alias)
            assertNotNull(cached)
            assertEquals(newKey1, cached)
        }
        val newKey2 = WrappingKey.generateWrappingKey(schemeMetadata)
        cache.act {
            it.saveWrappingKey(alias, newKey2, false)
        }
        cache.act {
            val cached = it.findWrappingKey(alias)
            assertNotNull(cached)
            assertEquals(newKey1, cached)
        }
    }

    @Test
    fun `Should fail saving same public key`() {
        val tenantId1 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val cache = createSigningKeyCacheImpl()
        cache.act(tenantId1) { it.save(p1) }
        assertThrows(PersistenceException::class.java) {
            cache.act(tenantId1) {
                it.save(p1)
            }
        }
        cache.act(tenantId1) { it.save(w1) }
        assertThrows(PersistenceException::class.java) {
            cache.act(tenantId1) {
                it.save(w1)
            }
        }
    }

    @Test
    fun `Should save same public keys for difefrent tenants and fetch them separately`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val cache = createSigningKeyCacheImpl()
        cache.act(tenantId1) { it.save(p1) }
        cache.act(tenantId2) { it.save(p1) }
        cache.act(tenantId1) { it.save(w1) }
        cache.act(tenantId2) { it.save(w1) }
        val keyP11 = cache.act(tenantId1) {
            it.lookup(listOf(p1.key.publicKey.publicKeyId()))
        }
        assertEquals(1, keyP11.size)
        assertEquals(tenantId1, p1, keyP11.first())
        val keyP12 = cache.act(tenantId2) {
            it.lookup(listOf(p1.key.publicKey.publicKeyId()))
        }
        assertEquals(1, keyP12.size)
        assertEquals(tenantId2, p1, keyP12.first())
        val keyW11 = cache.act(tenantId1) {
            it.lookup(listOf(w1.key.publicKey.publicKeyId()))
        }
        assertEquals(1, keyW11.size)
        assertEquals(tenantId1, w1, keyW11.first())
        val keyW12 = cache.act(tenantId2) {
            it.lookup(listOf(w1.key.publicKey.publicKeyId()))
        }
        assertEquals(1, keyW12.size)
        assertEquals(tenantId2, w1, keyW12.first())
    }

    @Test
    fun `Should save public keys find by alias`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p12 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.SESSION, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val cache = createSigningKeyCacheImpl()
        cache.act(tenantId1) { it.save(p1) }
        cache.act(tenantId2) { it.save(p12) }
        cache.act(tenantId1) { it.save(p2) }
        cache.act(tenantId1) { it.save(p3) }
        cache.act(tenantId1) { it.save(p4) }
        val keyNotFoundByAlias = cache.act(tenantId1) {
            it.find(UUID.randomUUID().toString())
        }
        assertNull(keyNotFoundByAlias)
        val keyByAlias = cache.act(tenantId1) {
            it.find(p4.alias!!)
        }
        assertEquals(tenantId1, p4, keyByAlias)
    }

    @Test
    fun `Should save public keys and lookup keys by id`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p12 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.SESSION, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w12 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val cache = createSigningKeyCacheImpl()
        cache.act(tenantId1) { it.save(p1) }
        cache.act(tenantId2) { it.save(p12) }
        cache.act(tenantId1) { it.save(p2) }
        cache.act(tenantId1) { it.save(p3) }
        cache.act(tenantId1) { it.save(p4) }
        cache.act(tenantId1) { it.save(w1) }
        cache.act(tenantId2) { it.save(w12) }
        cache.act(tenantId1) { it.save(w2) }
        cache.act(tenantId1) { it.save(w3) }
        val keys = cache.act(tenantId1) {
            it.lookup(
                listOf(
                    p1.key.publicKey.publicKeyId(),
                    p12.key.publicKey.publicKeyId(),
                    p3.key.publicKey.publicKeyId(),
                    w2.key.publicKey.publicKeyId()
                )
            )
        }
        assertEquals(3, keys.size)
        assertEquals(tenantId1, p1, keys.firstOrNull { it.id == p1.key.publicKey.publicKeyId() })
        assertEquals(tenantId1, p3, keys.firstOrNull { it.id == p3.key.publicKey.publicKeyId() })
        assertEquals(tenantId1, w2, keys.firstOrNull { it.id == w2.key.publicKey.publicKeyId() })
    }

    @Test
    fun `Should save public keys and find by public key multiple times`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p12 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.SESSION, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w12 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val cache = createSigningKeyCacheImpl()
        cache.act(tenantId1) { it.save(p1) }
        cache.act(tenantId2) { it.save(p12) }
        cache.act(tenantId1) { it.save(p2) }
        cache.act(tenantId1) { it.save(p3) }
        cache.act(tenantId1) { it.save(p4) }
        cache.act(tenantId1) { it.save(w1) }
        cache.act(tenantId2) { it.save(w12) }
        cache.act(tenantId1) { it.save(w2) }
        cache.act(tenantId1) { it.save(w3) }
        val keyNotFound = cache.act(tenantId1) {
            it.find(generateKeyPair(EDDSA_ED25519_CODE_NAME).public)
        }
        assertNull(keyNotFound)
        val keyByItsOwn1 = cache.act(tenantId1) {
            it.find(p2.key.publicKey)
        }
        assertEquals(tenantId1, p2, keyByItsOwn1)
        val keyByItsOwn2 = cache.act(tenantId1) {
            it.find(p2.key.publicKey)
        }
        assertEquals(tenantId1, p2, keyByItsOwn2)
        val keyByItsOwn3 = cache.act(tenantId1) {
            it.find(w2.key.publicKey)
        }
        assertEquals(tenantId1, w2, keyByItsOwn3)
        val keyByItsOwn4 = cache.act(tenantId1) {
            it.find(w2.key.publicKey)
        }
        assertEquals(tenantId1, w2, keyByItsOwn4)
    }

    @Test
    fun `Should save public keys and key material and do various lookups for them`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p12 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.SESSION, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w12 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val cache = createSigningKeyCacheImpl()
        cache.act(tenantId1) { it.save(p1) }
        cache.act(tenantId2) { it.save(p12) }
        cache.act(tenantId1) { it.save(p2) }
        cache.act(tenantId1) { it.save(p3) }
        cache.act(tenantId1) { it.save(p4) }
        cache.act(tenantId1) { it.save(w1) }
        cache.act(tenantId2) { it.save(w12) }
        cache.act(tenantId1) { it.save(w2) }
        cache.act(tenantId1) { it.save(w3) }
        val result1 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 10,
                SigningKeyOrderBy.ALIAS,
                mapOf(
                    CATEGORY_FILTER to CryptoConsts.HsmCategories.LEDGER
                )
            )
        }
        assertEquals(2, result1.size)
        listOf(p1, p4).sortedBy { it.alias }.forEachIndexed { i, o ->
            assertEquals(tenantId1, o, result1.elementAt(i))
        }
        val result2 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 10,
                SigningKeyOrderBy.ALIAS_DESC,
                mapOf(
                    CATEGORY_FILTER to CryptoConsts.HsmCategories.LEDGER
                )
            )
        }
        assertEquals(2, result2.size)
        listOf(p1, p4).sortedByDescending { it.alias }.forEachIndexed { i, o ->
            assertEquals(tenantId1, o, result2.elementAt(i))
        }
        val result3 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 10,
                SigningKeyOrderBy.ALIAS_DESC,
                mapOf(
                    CATEGORY_FILTER to CryptoConsts.HsmCategories.FRESH_KEYS,
                    SCHEME_CODE_NAME_FILTER to EDDSA_ED25519_CODE_NAME
                )
            )
        }
        assertEquals(1, result3.size)
        assertEquals(tenantId1, w1, result3.first())
        val result4 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 10,
                SigningKeyOrderBy.NONE,
                mapOf(
                    ALIAS_FILTER to p2.alias!!,
                    SCHEME_CODE_NAME_FILTER to ECDSA_SECP256R1_CODE_NAME,
                    CATEGORY_FILTER to CryptoConsts.HsmCategories.TLS
                )
            )
        }
        assertEquals(1, result4.size)
        assertEquals(tenantId1, p2, result4.first())
        val result5 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 10,
                SigningKeyOrderBy.CATEGORY_DESC,
                mapOf(
                    MASTER_KEY_ALIAS_FILTER to w3.masterKeyAlias!!,
                    SCHEME_CODE_NAME_FILTER to ECDSA_SECP256R1_CODE_NAME,
                    CATEGORY_FILTER to CryptoConsts.HsmCategories.FRESH_KEYS,
                    EXTERNAL_ID_FILTER to w3.externalId!!
                )
            )
        }
        assertEquals(1, result5.size)
        assertEquals(tenantId1, w3, result5.first())
        val result6 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 20,
                SigningKeyOrderBy.ID,
                mapOf(
                    CREATED_AFTER_FILTER to Instant.now().minusSeconds(300).toString(),
                    CREATED_BEFORE_FILTER to Instant.now().minusSeconds(-1).toString()
                )
            )
        }
        assertEquals(7, result6.size)
        listOf(p1, p2, p3, p4, w1, w2, w3).sortedBy {
            when (it) {
                is SigningPublicKeySaveContext -> it.key.publicKey.publicKeyId()
                is SigningWrappedKeySaveContext -> it.key.publicKey.publicKeyId()
                else -> throw IllegalArgumentException()
            }
        }.forEachIndexed { i, o ->
            when (o) {
                is SigningPublicKeySaveContext -> assertEquals(tenantId1, o, result6.elementAt(i))
                is SigningWrappedKeySaveContext -> assertEquals(tenantId1, o, result6.elementAt(i))
                else -> throw IllegalArgumentException()
            }
        }
    }

    @Test
    fun `Should save public keys and key material and do paged lookups for them`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p12 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.SESSION, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningPublicKeySaveContext(CryptoConsts.HsmCategories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w12 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val cache = createSigningKeyCacheImpl()
        cache.act(tenantId1) { it.save(p1) }
        cache.act(tenantId2) { it.save(p12) }
        cache.act(tenantId1) { it.save(p2) }
        cache.act(tenantId1) { it.save(p3) }
        cache.act(tenantId1) { it.save(p4) }
        cache.act(tenantId1) { it.save(w1) }
        cache.act(tenantId2) { it.save(w12) }
        cache.act(tenantId1) { it.save(w2) }
        cache.act(tenantId1) { it.save(w3) }
        val page1 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 2,
                SigningKeyOrderBy.ID,
                mapOf(
                    SCHEME_CODE_NAME_FILTER to EDDSA_ED25519_CODE_NAME
                )
            )
        }
        assertEquals(2, page1.size)
        listOf(p1, p3, p4, w1).sortedBy {
            when (it) {
                is SigningPublicKeySaveContext -> it.key.publicKey.publicKeyId()
                is SigningWrappedKeySaveContext -> it.key.publicKey.publicKeyId()
                else -> throw IllegalArgumentException()
            }
        }.drop(0).take(2).forEachIndexed { i, o ->
            when (o) {
                is SigningPublicKeySaveContext -> assertEquals(tenantId1, o, page1.elementAt(i))
                is SigningWrappedKeySaveContext -> assertEquals(tenantId1, o, page1.elementAt(i))
                else -> throw IllegalArgumentException()
            }
        }
        val page2 = cache.act(tenantId1) {
            it.lookup(
                skip = 2,
                take = 2,
                SigningKeyOrderBy.ID,
                mapOf(
                    SCHEME_CODE_NAME_FILTER to EDDSA_ED25519_CODE_NAME
                )
            )
        }
        assertEquals(2, page2.size)
        listOf(p1, p3, p4, w1).sortedBy {
            when (it) {
                is SigningPublicKeySaveContext -> it.key.publicKey.publicKeyId()
                is SigningWrappedKeySaveContext -> it.key.publicKey.publicKeyId()
                else -> throw IllegalArgumentException()
            }
        }.drop(2).take(2).forEachIndexed { i, o ->
            when (o) {
                is SigningPublicKeySaveContext -> assertEquals(tenantId1, o, page2.elementAt(i))
                is SigningWrappedKeySaveContext -> assertEquals(tenantId1, o, page2.elementAt(i))
                else -> throw IllegalArgumentException()
            }
        }
        val page3 = cache.act(tenantId1) {
            it.lookup(
                skip = 4,
                take = 2,
                SigningKeyOrderBy.ID,
                mapOf(
                    SCHEME_CODE_NAME_FILTER to EDDSA_ED25519_CODE_NAME
                )
            )
        }
        assertEquals(0, page3.size)
    }

    private fun assertEquals(tenantId: String, expected: SigningPublicKeySaveContext, actual: SigningCachedKey?) {
        assertNotNull(actual)
        assertEquals(expected.key.publicKey.publicKeyId(), actual!!.id)
        assertEquals(tenantId, actual.tenantId)
        assertEquals(expected.category, actual.category)
        assertEquals(expected.alias, actual.alias)
        assertEquals(expected.key.hsmAlias, actual.hsmAlias)
        assertArrayEquals(expected.key.publicKey.encoded, actual.publicKey)
        assertNull(actual.keyMaterial)
        assertEquals(expected.signatureScheme.codeName, actual.schemeCodeName)
        assertNull(actual.masterKeyAlias)
        assertEquals(expected.externalId, actual.externalId)
        assertNull(actual.encodingVersion)
        val now = Instant.now()
        assertTrue(actual.created >= now.minusSeconds(60))
        assertTrue(actual.created <= now.minusSeconds(-1))
    }

    private fun assertEquals(tenantId: String, expected: SigningWrappedKeySaveContext, actual: SigningCachedKey?) {
        assertNotNull(actual)
        assertEquals(expected.key.publicKey.publicKeyId(), actual!!.id)
        assertEquals(tenantId, actual.tenantId)
        assertEquals(expected.category, actual.category)
        assertEquals(expected.alias, actual.alias)
        assertNull(actual.hsmAlias)
        assertArrayEquals(expected.key.publicKey.encoded, actual.publicKey)
        assertArrayEquals(expected.key.keyMaterial, actual.keyMaterial)
        assertEquals(expected.signatureScheme.codeName, actual.schemeCodeName)
        assertEquals(expected.masterKeyAlias, actual.masterKeyAlias)
        assertEquals(expected.externalId, actual.externalId)
        assertEquals(expected.key.encodingVersion, actual.encodingVersion)
        val now = Instant.now()
        assertTrue(actual.created >= now.minusSeconds(60))
        assertTrue(actual.created <= now.minusSeconds(-1))
    }

    private fun createSigningKeyCacheImpl() = SigningKeyCacheImpl(
        config = createDefaultCryptoConfig(KeyCredentials("salt", "passphrase")).signingPersistence(),
        dbConnectionOps = object : DbConnectionOps {
            override fun putConnection(
                name: String,
                privilege: DbPrivilege,
                config: SmartConfig,
                description: String?,
                updateActor: String
            ): UUID = throw NotImplementedError()

            override fun putConnection(
                entityManager: EntityManager,
                name: String,
                privilege: DbPrivilege,
                config: SmartConfig,
                description: String?,
                updateActor: String
            ): UUID = throw NotImplementedError()

            override fun getClusterDataSource(): DataSource = throw NotImplementedError()
            override fun getDataSource(name: String, privilege: DbPrivilege): DataSource? = null
            override fun getDataSource(config: SmartConfig): CloseableDataSource = throw NotImplementedError()
            override fun getClusterEntityManagerFactory(): EntityManagerFactory = throw NotImplementedError()
            override fun getOrCreateEntityManagerFactory(
                db: CordaDb,
                privilege: DbPrivilege
            ): EntityManagerFactory =
                if (db == CordaDb.Crypto && privilege == DbPrivilege.DML) {
                    cryptoEmf
                } else {
                    throw IllegalArgumentException()
                }

            override fun getOrCreateEntityManagerFactory(
                name: String,
                privilege: DbPrivilege,
                entitiesSet: JpaEntitiesSet
            ): EntityManagerFactory =
                if (name.startsWith("vnode_crypto_") && privilege == DbPrivilege.DML) {
                    cryptoEmf
                } else {
                    throw IllegalArgumentException()
                }

            override fun createEntityManagerFactory(
                connectionId: UUID,
                entitiesSet: JpaEntitiesSet
            ): EntityManagerFactory {
                TODO("Not yet implemented")
            }
        },
        jpaEntitiesRegistry = object : JpaEntitiesRegistry {
            override val all: Set<JpaEntitiesSet> get() = throw NotImplementedError()
            override fun get(persistenceUnitName: String): JpaEntitiesSet? =
                if (persistenceUnitName == CordaDb.Crypto.persistenceUnitName) {
                    JpaEntitiesSet.create(CordaDb.Crypto.persistenceUnitName, CryptoEntities.classes)
                } else {
                    null
                }

            override fun register(persistenceUnitName: String, jpeEntities: Set<Class<*>>) =
                throw NotImplementedError()
        },
        layeredPropertyMapFactory = layeredPropertyMapFactory,
        keyEncodingService = schemeMetadata
    )

    private fun createSigningWrappedKeySaveContext(
        schemeCodeName: String
    ): SigningWrappedKeySaveContext {
        val keyPair = generateKeyPair(schemeCodeName)
        return SigningWrappedKeySaveContext(
            key = GeneratedWrappedKey(
                publicKey = keyPair.public,
                keyMaterial = keyPair.private.encoded,
                encodingVersion = 1
            ),
            masterKeyAlias = UUID.randomUUID().toString(),
            externalId = UUID.randomUUID().toString(),
            alias = null,
            category = CryptoConsts.HsmCategories.FRESH_KEYS,
            signatureScheme = schemeMetadata.findSignatureScheme(schemeCodeName)
        )
    }

    private fun createSigningPublicKeySaveContext(
        category: String,
        schemeCodeName: String
    ): SigningPublicKeySaveContext {
        val keyPair = generateKeyPair(schemeCodeName)
        return SigningPublicKeySaveContext(
            key = GeneratedPublicKey(
                publicKey = keyPair.public,
                hsmAlias = UUID.randomUUID().toString()
            ),
            alias = UUID.randomUUID().toString(),
            category = category,
            signatureScheme = schemeMetadata.findSignatureScheme(schemeCodeName),
            externalId = UUID.randomUUID().toString()
        )
    }
}