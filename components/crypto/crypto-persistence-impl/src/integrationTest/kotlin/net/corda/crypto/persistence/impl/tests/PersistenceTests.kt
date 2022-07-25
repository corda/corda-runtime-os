package net.corda.crypto.persistence.impl.tests

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.impl.tests.infra.TestDependenciesTracker
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.persistence.signing.SigningCachedKey
import net.corda.crypto.persistence.signing.SigningPublicKeySaveContext
import net.corda.crypto.persistence.signing.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityPrimaryKey
import net.corda.crypto.persistence.db.model.SigningKeyEntityStatus
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.crypto.persistence.impl.tests.infra.CryptoDBSetup
import net.corda.crypto.persistence.impl.tests.infra.makeBootstrapConfig
import net.corda.crypto.persistence.impl.tests.infra.makeMessagingConfig
import net.corda.crypto.persistence.signing.SigningKeyStatus
import net.corda.crypto.persistence.signing.SigningKeyStore
import net.corda.crypto.persistence.wrapping.WrappingKeyStore
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.base.util.toHex
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.publicKeyId
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class, DBSetup::class)
class PersistenceTests {
    companion object {
        private val CLIENT_ID = "${PersistenceTests::class.java}-integration-test"

        @InjectService(timeout = 5000)
        lateinit var layeredPropertyMapFactory: LayeredPropertyMapFactory

        @InjectService(timeout = 5000)
        lateinit var schemeMetadata: CipherSchemeMetadata

        @InjectService(timeout = 5000L)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 5000)
        lateinit var cryptoConnectionsFactory: CryptoConnectionsFactory

        @InjectService(timeout = 5000)
        lateinit var hsmStore: HSMStore

        @InjectService(timeout = 5000)
        lateinit var signingKeyStore: SigningKeyStore

        @InjectService(timeout = 5000)
        lateinit var wrappingKeyStore: WrappingKeyStore

        private lateinit var publisher: Publisher

        private lateinit var tracker: TestDependenciesTracker

        private val boostrapConfig = makeBootstrapConfig(
            mapOf(
                BootConfig.BOOT_DB_PARAMS to CryptoDBSetup.clusterDb.config
            )
        )

        private val messagingConfig = makeMessagingConfig(boostrapConfig)

        @JvmStatic
        @BeforeAll
        fun setup() {
            publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), messagingConfig)
            CryptoDBSetup.setup()
            setupConfiguration()
            setupDependencies()
            waitForVirtualNodeInfoReady()
        }

        private fun setupConfiguration() {
            val cryptoConfig = createDefaultCryptoConfig(
                KeyCredentials("passphrase", "salt")
            ) .root().render()
            val virtualNodeInfo = VirtualNodeInfo(
                holdingIdentity = CryptoDBSetup.vNodeHoldingIdentity,
                cpiIdentifier = CpiIdentifier(
                    name = "cpi",
                    version = "1",
                    signerSummaryHash = null
                ),
                cryptoDmlConnectionId = CryptoDBSetup.connectionId(CryptoDBSetup.vnodeDb.name),
                vaultDmlConnectionId = UUID.randomUUID(),
                timestamp = Instant.now()
            )
            publisher.publish(
                listOf(
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.MESSAGING_CONFIG,
                        Configuration(
                            messagingConfig.root().render(),
                            messagingConfig.root().render(),
                            0,
                            ConfigurationSchemaVersion(1, 0)
                        )
                    ),
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.CRYPTO_CONFIG,
                        Configuration(
                            cryptoConfig,
                            cryptoConfig,
                            0,
                            ConfigurationSchemaVersion(1, 0)
                        )
                    ),
                    Record(
                        Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
                        virtualNodeInfo.holdingIdentity.toAvro(),
                        virtualNodeInfo.toAvro()
                    )
                )
            )
        }

        private fun setupDependencies() {
            tracker = TestDependenciesTracker.create(
                LifecycleCoordinatorName.forComponent<PersistenceTests>(),
                setOf(
                    ConfigurationReadService::class.java,
                    DbConnectionManager::class.java,
                    VirtualNodeInfoReadService::class.java,
                    CryptoConnectionsFactory::class.java,
                    HSMStore::class.java,
                    SigningKeyStore::class.java,
                    HSMStore::class.java
                )
            )
            tracker.component<ConfigurationReadService>().bootstrapConfig(boostrapConfig)
            tracker.component<DbConnectionManager>().bootstrap(boostrapConfig.getConfig(BootConfig.BOOT_DB_PARAMS))
            tracker.waitUntilAllUp(Duration.ofSeconds(60))
        }

        private fun waitForVirtualNodeInfoReady() {
            eventually {
                val info =  tracker.component<VirtualNodeInfoReadService>().get(CryptoDBSetup.vNodeHoldingIdentity)
                assertNotNull(info)
                assertEquals(CryptoDBSetup.connectionId(CryptoDBSetup.vnodeDb.name), info!!.cryptoDmlConnectionId)
            }
        }

        private fun randomTenantId() = publicKeyIdFromBytes(UUID.randomUUID().toString().toByteArray())

        private fun emf(tenant: String): EntityManagerFactory = cryptoConnectionsFactory.getEntityManagerFactory(
            tenant
        )

        private fun generateKeyPair(schemeName: String): KeyPair {
            val scheme = schemeMetadata.findKeyScheme(schemeName)
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
        deprecatedAt: Long = 0
    ): HSMCategoryAssociationEntity {
        val workerSetId = UUID.randomUUID().toString()
        val associationId = UUID.randomUUID().toString()
        val categoryAssociationId = UUID.randomUUID().toString()
        val association = HSMAssociationEntity(
            id = associationId,
            tenantId = tenantId,
            workerSetId = workerSetId,
            timestamp = Instant.now(),
            masterKeyAlias = if (masterKeyPolicy == MasterKeyPolicy.UNIQUE) {
                UUID.randomUUID().toString().toByteArray().toHex().take(30)
            } else {
                null
            },
            aliasSecret = "Hello World!".toByteArray()
        )
        emf(CryptoTenants.CRYPTO).transaction { em ->
            em.persist(association)
        }
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = categoryAssociationId,
            tenantId = tenantId,
            category = category,
            hsmAssociation = association,
            timestamp = Instant.now(),
            deprecatedAt = deprecatedAt
        )
        emf(CryptoTenants.CRYPTO).transaction { em ->
            em.persist(categoryAssociation)
        }
        return categoryAssociation
    }

    private fun assertHSMCategoryAssociationEntity(
        expected: HSMCategoryAssociationEntity,
        actual: HSMTenantAssociation?
    ) {
        assertNotNull(actual)
        assertEquals(expected.category, actual!!.category)
        assertEquals(expected.tenantId, actual.tenantId)
        assertEquals(expected.deprecatedAt, actual.deprecatedAt)
        assertEquals(expected.hsmAssociation.workerSetId, actual.workerSetId)
        assertEquals(expected.hsmAssociation.tenantId, actual.tenantId)
        assertEquals(expected.hsmAssociation.masterKeyAlias, actual.masterKeyAlias)
        assertArrayEquals(expected.hsmAssociation.aliasSecret, actual.aliasSecret)
    }

    private fun assertSigningCachedKey(
        tenantId: String,
        expected: SigningPublicKeySaveContext,
        actual: SigningCachedKey?
    ) {
        assertNotNull(actual)
        assertEquals(expected.key.publicKey.publicKeyId(), actual!!.id)
        assertEquals(tenantId, actual.tenantId)
        assertEquals(expected.category, actual.category)
        assertEquals(expected.alias, actual.alias)
        assertEquals(expected.key.hsmAlias, actual.hsmAlias)
        assertArrayEquals(expected.key.publicKey.encoded, actual.publicKey)
        assertNull(actual.keyMaterial)
        assertEquals(expected.keyScheme.codeName, actual.schemeCodeName)
        assertNull(actual.masterKeyAlias)
        assertEquals(expected.externalId, actual.externalId)
        assertNull(actual.encodingVersion)
        val now = Instant.now()
        assertTrue(actual.timestamp >= now.minusSeconds(60))
        assertTrue(actual.timestamp <= now.minusSeconds(-1))
        assertEquals(expected.workerSetId, actual.workerSetId)
        assertEquals(SigningKeyStatus.NORMAL, actual.status)
    }

    private fun assertSigningCachedKey(
        tenantId: String,
        expected: SigningWrappedKeySaveContext,
        actual: SigningCachedKey?
    ) {
        assertNotNull(actual)
        assertEquals(expected.key.publicKey.publicKeyId(), actual!!.id)
        assertEquals(tenantId, actual.tenantId)
        assertEquals(expected.category, actual.category)
        assertEquals(expected.alias, actual.alias)
        assertNull(actual.hsmAlias)
        assertArrayEquals(expected.key.publicKey.encoded, actual.publicKey)
        assertArrayEquals(expected.key.keyMaterial, actual.keyMaterial)
        assertEquals(expected.keyScheme.codeName, actual.schemeCodeName)
        assertEquals(expected.masterKeyAlias, actual.masterKeyAlias)
        assertEquals(expected.externalId, actual.externalId)
        assertEquals(expected.key.encodingVersion, actual.encodingVersion)
        val now = Instant.now()
        assertTrue(actual.timestamp >= now.minusSeconds(60))
        assertTrue(actual.timestamp <= now.minusSeconds(-1))
        assertEquals(expected.workerSetId, actual.workerSetId)
    }

    private fun createSigningWrappedKeySaveContext(
        workerSetId: String,
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
            category = CryptoConsts.Categories.CI,
            keyScheme = schemeMetadata.findKeyScheme(schemeCodeName),
            workerSetId = workerSetId
        )
    }

    private fun createSigningPublicKeySaveContext(
        workerSetId: String,
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
            keyScheme = schemeMetadata.findKeyScheme(schemeCodeName),
            externalId = UUID.randomUUID().toString(),
            workerSetId = workerSetId
        )
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
        emf(CryptoTenants.CRYPTO).transaction { em ->
            em.persist(entity)
        }
        emf(CryptoTenants.CRYPTO).use { em ->
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
            timestamp = Instant.now(),
            category = CryptoConsts.Categories.LEDGER,
            schemeCodeName = RSA_CODE_NAME,
            publicKey = keyPair.public.encoded,
            keyMaterial = keyPair.private.encoded,
            encodingVersion = 11,
            masterKeyAlias = UUID.randomUUID().toString(),
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            externalId = UUID.randomUUID().toString(),
            workerSetId = UUID.randomUUID().toString(),
            status = SigningKeyEntityStatus.NORMAL
        )
        emf(CryptoTenants.CRYPTO).transaction { em ->
            em.persist(entity)
        }
        emf(CryptoTenants.CRYPTO).use { em ->
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
                entity.timestamp.epochSecond,
                retrieved.timestamp.epochSecond
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
            assertEquals(entity.workerSetId, retrieved.workerSetId)
        }
    }

    @Test
    fun `Should persist and retrieve raw HSM related entities`() {
        val tenantId = randomTenantId()
        val workerSetId = UUID.randomUUID().toString()
        val associationId = UUID.randomUUID().toString()
        val categoryAssociationId = UUID.randomUUID().toString()
        val association = HSMAssociationEntity(
            id = associationId,
            tenantId = tenantId,
            workerSetId = workerSetId,
            timestamp = Instant.now(),
            masterKeyAlias = UUID.randomUUID().toString().toByteArray().toHex().take(30),
            aliasSecret = "Hello World!".toByteArray()
        )
        emf(CryptoTenants.CRYPTO).transaction { em ->
            em.persist(association)
        }
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = categoryAssociationId,
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            hsmAssociation = association,
            timestamp = Instant.now(),
            deprecatedAt = 0
        )
        emf(CryptoTenants.CRYPTO).transaction { em ->
            em.persist(categoryAssociation)
        }
        emf(CryptoTenants.CRYPTO).use { em ->
            val retrieved = em.find(HSMCategoryAssociationEntity::class.java, categoryAssociationId)
            assertNotNull(retrieved)
            assertNotSame(categoryAssociation, retrieved)
            assertEquals(categoryAssociationId, retrieved.id)
            assertEquals(CryptoConsts.Categories.LEDGER, retrieved.category)
            assertNotSame(association, retrieved.hsmAssociation)
            assertEquals(associationId, retrieved.hsmAssociation.id)
            assertEquals(tenantId, retrieved.hsmAssociation.tenantId)
            assertEquals(association.masterKeyAlias, retrieved.hsmAssociation.masterKeyAlias)
            assertArrayEquals(association.aliasSecret, retrieved.hsmAssociation.aliasSecret)
        }
    }
/*
    @Test
    fun `Should fail to save HSMAssociationEntity with duplicate tenant and configuration`() {
        val tenantId = randomTenantId()
        val a1 = createAndPersistHSMEntities(tenantId, CryptoConsts.Categories.LEDGER, MasterKeyPolicy.NEW)
        val association = HSMAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = a1.hsmAssociation.tenantId,
            config = a1.hsmAssociation.config,
            timestamp = Instant.now(),
            masterKeyAlias = UUID.randomUUID().toString().toByteArray().toHex().take(30),
            aliasSecret = "Hello World!".toByteArray()
        )
        assertThrows(PersistenceException::class.java) {
            emf(CryptoTenants.CRYPTO).transaction { em ->
                em.persist(association)
            }
        }
    }

    @Test
    fun `Should not fail to save HSMCategoryAssociationEntity with duplicate category and association`() {
        val tenantId = randomTenantId()
        val a1 = createAndPersistHSMEntities(tenantId, CryptoConsts.Categories.LEDGER, MasterKeyPolicy.NEW)
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            category = a1.category,
            hsmAssociation = a1.hsmAssociation,
            timestamp = Instant.now(),
            deprecatedAt = Instant.now().toEpochMilli()
        )
        emf(CryptoTenants.CRYPTO).transaction { em ->
            em.persist(categoryAssociation)
        }
    }

    @Test
    fun `Should fail to save HSMCategoryAssociationEntity with duplicate tenant, category and deprecation`() {
        val tenantId = randomTenantId()
        val a1 = createAndPersistHSMEntities(tenantId, CryptoConsts.Categories.LEDGER, MasterKeyPolicy.NEW)
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            category = a1.category,
            hsmAssociation = a1.hsmAssociation,
            timestamp = Instant.now(),
            deprecatedAt = 0
        )
        assertThrows(PersistenceException::class.java) {
            emf(CryptoTenants.CRYPTO).transaction { em ->
                em.persist(categoryAssociation)
            }
        }
    }

    @Test
    fun `findTenantAssociation(tenantId, category) should not return deprecated associations`() {
        val cache = createHSMStoreImpl()
        val tenantId1 = randomTenantId()
        val a1 = createAndPersistHSMEntities(
            tenantId = tenantId1,
            category = CryptoConsts.Categories.LEDGER,
            masterKeyPolicy = MasterKeyPolicy.NEW,
            deprecatedAt = Instant.now().toEpochMilli()
        )
        val r1 = cache.act { it.findTenantAssociation(tenantId1, CryptoConsts.Categories.LEDGER) }
        assertNull(r1)
        val a2 = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = a1.tenantId,
            category = a1.category,
            hsmAssociation = a1.hsmAssociation,
            timestamp = Instant.now(),
            deprecatedAt = 0
        )
        emf(CryptoTenants.CRYPTO).transaction { em ->
            em.persist(a2)
        }
        val r2 = cache.act { it.findTenantAssociation(tenantId1, CryptoConsts.Categories.LEDGER) }
        assertHSMCategoryAssociationEntity(a2, r2)
    }

    @Test
    fun `findTenantAssociation(tenantId, category) should be able to find tenant HSM associations with categories`() {
        val tenantId1 = randomTenantId()
        val a1 = createAndPersistHSMEntities(tenantId1, CryptoConsts.Categories.LEDGER, MasterKeyPolicy.NEW)
        val a2 = createAndPersistHSMEntities(tenantId1, CryptoConsts.Categories.SESSION_INIT, MasterKeyPolicy.SHARED)
        val cache = createHSMStoreImpl()
        val r1 = cache.act { it.findTenantAssociation(tenantId1, CryptoConsts.Categories.LEDGER) }
        val r2 = cache.act { it.findTenantAssociation(tenantId1, CryptoConsts.Categories.SESSION_INIT) }
        assertHSMCategoryAssociationEntity(a1, r1)
        assertHSMCategoryAssociationEntity(a2, r2)
    }

    @Test
    fun `findTenantAssociation(tenantId, category) should return null when parameters are not matching`() {
        val tenantId = randomTenantId()
        createAndPersistHSMEntities(tenantId, CryptoConsts.Categories.LEDGER, MasterKeyPolicy.NEW)
        val cache = createHSMStoreImpl()
        val r1 = cache.act { it.findTenantAssociation(tenantId, CryptoConsts.Categories.SESSION_INIT) }
        assertNull(r1)
        val r2 = cache.act { it.findTenantAssociation(randomTenantId(), CryptoConsts.Categories.LEDGER) }
        assertNull(r2)
    }

    @Test
    fun `findTenantAssociation(associationId) should be able to find tenant HSM associations by its id`() {
        val tenantId1 = randomTenantId()
        val a1 = createAndPersistHSMEntities(tenantId1, CryptoConsts.Categories.LEDGER, MasterKeyPolicy.NEW)
        val a2 = createAndPersistHSMEntities(tenantId1, CryptoConsts.Categories.SESSION_INIT, MasterKeyPolicy.SHARED)
        val cache = createHSMStoreImpl()
        val r1 = cache.act { it.findTenantAssociation(a1.id) }
        val r2 = cache.act { it.findTenantAssociation(a2.id) }
        assertHSMCategoryAssociationEntity(a1, r1)
        assertHSMCategoryAssociationEntity(a2, r2)
    }

    @Test
    fun `findTenantAssociation(associationId) should return null when ids are not matching`() {
        val tenantId = randomTenantId()
        createAndPersistHSMEntities(tenantId, CryptoConsts.Categories.LEDGER, MasterKeyPolicy.NEW)
        val cache = createHSMStoreImpl()
        val r1 = cache.act { it.findTenantAssociation(UUID.randomUUID().toString()) }
        assertNull(r1)
        val r2 = cache.act { it.findTenantAssociation(UUID.randomUUID().toString()) }
        assertNull(r2)
    }

    @Test
    fun `findConfig should be able to find HSM config`() {
        val configId = UUID.randomUUID().toString()
        val expected = createAndPersistHSMConfigEntity(configId)
        val cache = createHSMStoreImpl()
        val actual = cache.act { it.findConfig(configId) }
        assertHSMConfig(expected, actual)
    }

    @Test
    fun `findConfig returns null when id is not matching`() {
        val configId = UUID.randomUUID().toString()
        createAndPersistHSMConfigEntity(configId)
        val cache = createHSMStoreImpl()
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
        val cache = createHSMStoreImpl()
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
        val fakeLedgeCategory = "LED-${UUID.randomUUID().toString().toByteArray().toHex().take(12)}"
        val fakeTLSCategory = "TLS-${UUID.randomUUID().toString().toByteArray().toHex().take(12)}"
        val fakeSessionCategory = "SES-${UUID.randomUUID().toString().toByteArray().toHex().take(12)}"
        val cache = createHSMStoreImpl()
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
                        fakeLedgeCategory,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
                    ),
                    HSMCategoryInfo(
                        fakeTLSCategory,
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
                        fakeLedgeCategory,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
                    ),
                    HSMCategoryInfo(
                        fakeTLSCategory,
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
            it.associate(tenantId1, fakeLedgeCategory, configId1)
            it.associate(tenantId2, fakeLedgeCategory, configId2)
            it.associate(tenantId3, fakeLedgeCategory, configId2)
            it.associate(tenantId4, fakeTLSCategory, configId2)
            it.associate(tenantId5, fakeTLSCategory, configId1)
        }
        val actual1 = cache.act { it.getHSMStats(fakeSessionCategory) }
        assertEquals(0, actual1.size)
        val actual2 = cache.act { it.getHSMStats(fakeLedgeCategory) }
        assertEquals(2, actual2.size)
        assertEquals(5, actual2.first { it.configId == configId1 }.capacity)
        assertEquals(2, actual2.first { it.configId == configId1 }.usages)
        assertEquals(3, actual2.first { it.configId == configId2 }.capacity)
        assertEquals(3, actual2.first { it.configId == configId2 }.usages)
        cache.act {
            it.associate(tenantId3, CryptoConsts.Categories.TLS, configId2)
        }
        val actual3 = cache.act { it.getHSMStats(fakeLedgeCategory) }
        assertEquals(2, actual3.size)
        assertEquals(5, actual3.first { it.configId == configId1 }.capacity)
        assertEquals(2, actual3.first { it.configId == configId1 }.usages)
        assertEquals(3, actual3.first { it.configId == configId2 }.capacity)
        assertEquals(4, actual3.first { it.configId == configId2 }.usages)
    }

    @Test
    fun `linkCategories should replace previous mapping`() {
        val cache = createHSMStoreImpl()
        val configId = UUID.randomUUID().toString()
        cache.act {
            it.add(createHSMInfo(configId = configId, capacity = 5), "{}".toByteArray())
        }
        cache.act {
            it.linkCategories(
                configId, listOf(
                    HSMCategoryInfo(
                        CryptoConsts.Categories.LEDGER,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
                    ),
                    HSMCategoryInfo(
                        CryptoConsts.Categories.TLS,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.BOTH
                    )
                )
            )
        }
        val mapping1 = emf(CryptoTenants.CRYPTO).use {
            getHSMCategoryMapEntities(it, configId)
        }
        assertEquals(2, mapping1.size)
        assertTrue(mapping1.any {
            it.category == CryptoConsts.Categories.LEDGER &&
                    it.keyPolicy == PrivateKeyPolicy.ALIASED
        })
        assertTrue(mapping1.any {
            it.category == CryptoConsts.Categories.TLS &&
                    it.keyPolicy == PrivateKeyPolicy.BOTH
        })
        cache.act {
            it.linkCategories(
                configId, listOf(
                    HSMCategoryInfo(
                        CryptoConsts.Categories.SESSION_INIT,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.WRAPPED
                    )
                )
            )
        }
        val mapping2 = emf(CryptoTenants.CRYPTO).use {
            getHSMCategoryMapEntities(it, configId)
        }
        assertEquals(1, mapping2.size)
        assertTrue(mapping2.any {
            it.category == CryptoConsts.Categories.SESSION_INIT &&
                    it.keyPolicy == PrivateKeyPolicy.WRAPPED
        })
    }

    @Test
    fun `Should return linked categories`() {
        val cache = createHSMStoreImpl()
        val configId = UUID.randomUUID().toString()
        cache.act {
            it.add(createHSMInfo(configId = configId, capacity = 5), "{}".toByteArray())
        }
        cache.act {
            it.linkCategories(
                configId, listOf(
                    HSMCategoryInfo(
                        CryptoConsts.Categories.LEDGER,
                        net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
                    ),
                    HSMCategoryInfo(
                        CryptoConsts.Categories.TLS,
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
            it.category == CryptoConsts.Categories.LEDGER &&
                    it.keyPolicy == net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
        })
        assertTrue(links.any {
            it.category == CryptoConsts.Categories.TLS &&
                    it.keyPolicy == net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.BOTH
        })
    }

    @Test
    fun `linkCategories should throw IllegalStateException if config does not exist`() {
        val cache = createHSMStoreImpl()
        cache.act {
            assertThrows(IllegalStateException::class.java) {
                it.linkCategories(
                    UUID.randomUUID().toString(), listOf(
                        HSMCategoryInfo(
                            CryptoConsts.Categories.LEDGER,
                            net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.ALIASED
                        ),
                        HSMCategoryInfo(
                            CryptoConsts.Categories.TLS,
                            net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.BOTH
                        )
                    )
                )
            }
        }
    }

    @Test
    fun `Should merge HSM configuration`() {
        val cache = createHSMStoreImpl()
        val info = createHSMInfo(configId = "", capacity = 5)
        val configId = cache.act {
            it.add(info, "{}".toByteArray())
        }
        val config1 = emf(CryptoTenants.CRYPTO).use {
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
        val config2 = emf(CryptoTenants.CRYPTO).use {
            it.find(HSMConfigEntity::class.java, updated.id)
        }
        assertEquals(configId, updated.id)
        assertHSMInfo(config2, updated)
    }

    @Test
    fun `Should be able to cache and then retrieve repeatedly wrapping keys`() {
        val cache = createWrappingKeyStoreImpl()
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
        val cache = createWrappingKeyStoreImpl()
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
        val cache = createWrappingKeyStoreImpl()
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
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val cache = createSigningKeyStoreImpl()
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
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val cache = createSigningKeyStoreImpl()
        cache.act(tenantId1) { it.save(p1) }
        cache.act(tenantId2) { it.save(p1) }
        cache.act(tenantId1) { it.save(w1) }
        cache.act(tenantId2) { it.save(w1) }
        val keyP11 = cache.act(tenantId1) {
            it.lookup(listOf(p1.key.publicKey.publicKeyId()))
        }
        assertEquals(1, keyP11.size)
        assertSigningCachedKey(tenantId1, p1, keyP11.first())
        val keyP12 = cache.act(tenantId2) {
            it.lookup(listOf(p1.key.publicKey.publicKeyId()))
        }
        assertEquals(1, keyP12.size)
        assertSigningCachedKey(tenantId2, p1, keyP12.first())
        val keyW11 = cache.act(tenantId1) {
            it.lookup(listOf(w1.key.publicKey.publicKeyId()))
        }
        assertEquals(1, keyW11.size)
        assertSigningCachedKey(tenantId1, w1, keyW11.first())
        val keyW12 = cache.act(tenantId2) {
            it.lookup(listOf(w1.key.publicKey.publicKeyId()))
        }
        assertEquals(1, keyW12.size)
        assertSigningCachedKey(tenantId2, w1, keyW12.first())
    }

    @Test
    fun `Should save public keys find by alias`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p12 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningPublicKeySaveContext(CryptoConsts.Categories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningPublicKeySaveContext(CryptoConsts.Categories.SESSION_INIT, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val cache = createSigningKeyStoreImpl()
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
        assertSigningCachedKey(tenantId1, p4, keyByAlias)
    }

    @Test
    fun `Should save public keys and lookup keys by id`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p12 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningPublicKeySaveContext(CryptoConsts.Categories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningPublicKeySaveContext(CryptoConsts.Categories.SESSION_INIT, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w12 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val cache = createSigningKeyStoreImpl()
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
        assertSigningCachedKey(tenantId1, p1, keys.firstOrNull { it.id == p1.key.publicKey.publicKeyId() })
        assertSigningCachedKey(tenantId1, p3, keys.firstOrNull { it.id == p3.key.publicKey.publicKeyId() })
        assertSigningCachedKey(tenantId1, w2, keys.firstOrNull { it.id == w2.key.publicKey.publicKeyId() })
    }

    @Test
    fun `Should save public keys and find by public key multiple times`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p12 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningPublicKeySaveContext(CryptoConsts.Categories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningPublicKeySaveContext(CryptoConsts.Categories.SESSION_INIT, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w12 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val cache = createSigningKeyStoreImpl()
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
        assertSigningCachedKey(tenantId1, p2, keyByItsOwn1)
        val keyByItsOwn2 = cache.act(tenantId1) {
            it.find(p2.key.publicKey)
        }
        assertSigningCachedKey(tenantId1, p2, keyByItsOwn2)
        val keyByItsOwn3 = cache.act(tenantId1) {
            it.find(w2.key.publicKey)
        }
        assertSigningCachedKey(tenantId1, w2, keyByItsOwn3)
        val keyByItsOwn4 = cache.act(tenantId1) {
            it.find(w2.key.publicKey)
        }
        assertSigningCachedKey(tenantId1, w2, keyByItsOwn4)
    }

    @Test
    fun `Should save public keys and key material and do various lookups for them`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p12 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningPublicKeySaveContext(CryptoConsts.Categories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningPublicKeySaveContext(CryptoConsts.Categories.SESSION_INIT, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w12 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val cache = createSigningKeyStoreImpl()
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
                    CATEGORY_FILTER to CryptoConsts.Categories.LEDGER
                )
            )
        }
        assertEquals(2, result1.size)
        listOf(p1, p4).sortedBy { it.alias }.forEachIndexed { i, o ->
            assertSigningCachedKey(tenantId1, o, result1.elementAt(i))
        }
        val result2 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 10,
                SigningKeyOrderBy.ALIAS_DESC,
                mapOf(
                    CATEGORY_FILTER to CryptoConsts.Categories.LEDGER
                )
            )
        }
        assertEquals(2, result2.size)
        listOf(p1, p4).sortedByDescending { it.alias }.forEachIndexed { i, o ->
            assertSigningCachedKey(tenantId1, o, result2.elementAt(i))
        }
        val result3 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 10,
                SigningKeyOrderBy.ALIAS_DESC,
                mapOf(
                    CATEGORY_FILTER to CryptoConsts.Categories.CI,
                    SCHEME_CODE_NAME_FILTER to EDDSA_ED25519_CODE_NAME
                )
            )
        }
        assertEquals(1, result3.size)
        assertSigningCachedKey(tenantId1, w1, result3.first())
        val result4 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 10,
                SigningKeyOrderBy.NONE,
                mapOf(
                    ALIAS_FILTER to p2.alias!!,
                    SCHEME_CODE_NAME_FILTER to ECDSA_SECP256R1_CODE_NAME,
                    CATEGORY_FILTER to CryptoConsts.Categories.TLS
                )
            )
        }
        assertEquals(1, result4.size)
        assertSigningCachedKey(tenantId1, p2, result4.first())
        val result5 = cache.act(tenantId1) {
            it.lookup(
                skip = 0,
                take = 10,
                SigningKeyOrderBy.CATEGORY_DESC,
                mapOf(
                    MASTER_KEY_ALIAS_FILTER to w3.masterKeyAlias!!,
                    SCHEME_CODE_NAME_FILTER to ECDSA_SECP256R1_CODE_NAME,
                    CATEGORY_FILTER to CryptoConsts.Categories.CI,
                    EXTERNAL_ID_FILTER to w3.externalId!!
                )
            )
        }
        assertEquals(1, result5.size)
        assertSigningCachedKey(tenantId1, w3, result5.first())
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
                is SigningPublicKeySaveContext -> assertSigningCachedKey(tenantId1, o, result6.elementAt(i))
                is SigningWrappedKeySaveContext -> assertSigningCachedKey(tenantId1, o, result6.elementAt(i))
                else -> throw IllegalArgumentException()
            }
        }
    }

    @Test
    fun `Should save public keys and key material and do paged lookups for them`() {
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val p1 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p12 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningPublicKeySaveContext(CryptoConsts.Categories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningPublicKeySaveContext(CryptoConsts.Categories.SESSION_INIT, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningPublicKeySaveContext(CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w12 = createSigningWrappedKeySaveContext(EDDSA_ED25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(ECDSA_SECP256R1_CODE_NAME)
        val cache = createSigningKeyStoreImpl()
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
                is SigningPublicKeySaveContext -> assertSigningCachedKey(tenantId1, o, page1.elementAt(i))
                is SigningWrappedKeySaveContext -> assertSigningCachedKey(tenantId1, o, page1.elementAt(i))
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
                is SigningPublicKeySaveContext -> assertSigningCachedKey(tenantId1, o, page2.elementAt(i))
                is SigningWrappedKeySaveContext -> assertSigningCachedKey(tenantId1, o, page2.elementAt(i))
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

 */
}
