package net.corda.crypto.persistence.impl.tests

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.EXTERNAL_ID_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.fullId
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeyStatus
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.WrappingKeyStore
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityPrimaryKey
import net.corda.crypto.persistence.db.model.SigningKeyEntityStatus
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.persistence.impl.tests.infra.CryptoConfigurationSetup
import net.corda.crypto.persistence.impl.tests.infra.CryptoDBSetup
import net.corda.crypto.persistence.impl.tests.infra.TestDependenciesTracker
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.schema.configuration.BootConfig
import net.corda.test.util.eventually
import net.corda.v5.base.util.EncodingUtils.toHex
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.X25519_CODE_NAME
import net.corda.v5.crypto.publicKeyId
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import javax.persistence.PersistenceException

@ExtendWith(ServiceExtension::class, DBSetup::class)
class PersistenceTests {
    companion object {
        private val CLIENT_ID = "${PersistenceTests::class.java}-integration-test"

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

        @JvmStatic
        @BeforeAll
        fun setup() {
            publisher = publisherFactory.createPublisher(
                PublisherConfig(CLIENT_ID),
                CryptoConfigurationSetup.messagingConfig
            )
            CryptoDBSetup.setup()
            CryptoConfigurationSetup.setup(publisher)
            setupDependencies()
            waitForVirtualNodeInfoReady()
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            // cleanup the connections
            cryptoConnectionsFactory.stop()
            eventually { assertFalse(cryptoConnectionsFactory.isRunning) }
        }


        @JvmStatic
        fun signingTenants(): Set<String> =
            CryptoTenants.allClusterTenants + CryptoDBSetup.vNodeHoldingIdentity.shortHash.value

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
                    WrappingKeyStore::class.java
                )
            )
            tracker.component<ConfigurationReadService>().bootstrapConfig(CryptoConfigurationSetup.boostrapConfig)
            tracker.component<DbConnectionManager>().bootstrap(
                CryptoConfigurationSetup.boostrapConfig.getConfig(BootConfig.BOOT_DB_PARAMS)
            )
            tracker.waitUntilAllUp(Duration.ofSeconds(60))
        }

        private fun waitForVirtualNodeInfoReady() {
            eventually {
                val info = tracker.component<VirtualNodeInfoReadService>().get(CryptoDBSetup.vNodeHoldingIdentity)
                assertNotNull(info)
                assertEquals(CryptoDBSetup.connectionId(CryptoDBSetup.vnodeDb.name), info!!.cryptoDmlConnectionId)
            }
        }

        private fun randomTenantId() = publicKeyIdFromBytes(UUID.randomUUID().toString().toByteArray())

        private fun cryptoDbEmf(): EntityManagerFactory = cryptoConnectionsFactory.getEntityManagerFactory(
            CryptoTenants.CRYPTO
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

        private fun createAndPersistHSMCategoryAssociationEntity(
            tenantId: String,
            category: String,
            masterKeyPolicy: MasterKeyPolicy = MasterKeyPolicy.SHARED,
            deprecatedAt: Long = 0
        ): HSMCategoryAssociationEntity {
            val hsmId = UUID.randomUUID().toString()
            val associationId = UUID.randomUUID().toString()
            val categoryAssociationId = UUID.randomUUID().toString()
            val association = HSMAssociationEntity(
                id = associationId,
                tenantId = tenantId,
                hsmId = hsmId,
                timestamp = Instant.now(),
                masterKeyAlias = if (masterKeyPolicy == MasterKeyPolicy.UNIQUE) {
                    toHex(UUID.randomUUID().toString().toByteArray()).take(30)
                } else {
                    null
                }
            )
            cryptoDbEmf().transaction { em ->
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
            cryptoDbEmf().transaction { em ->
                em.persist(categoryAssociation)
            }
            return categoryAssociation
        }

        private fun createSigningWrappedKeySaveContext(
            hsmId: String,
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
                hsmId = hsmId
            )
        }

        private fun createSigningKeySaveContext(
            hsmId: String,
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
                hsmId = hsmId
            )
        }

        private fun assertAssociation(
            expected: HSMCategoryAssociationEntity,
            actual: HSMAssociationInfo?
        ) {
            assertNotNull(actual)
            assertEquals(expected.category, actual!!.category)
            assertEquals(expected.tenantId, actual.tenantId)
            assertEquals(expected.deprecatedAt, actual.deprecatedAt)
            assertEquals(expected.hsmAssociation.hsmId, actual.hsmId)
            assertEquals(expected.hsmAssociation.tenantId, actual.tenantId)
            assertEquals(expected.hsmAssociation.masterKeyAlias, actual.masterKeyAlias)
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
            assertEquals(expected.hsmId, actual.hsmId)
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
            assertEquals(expected.hsmId, actual.hsmId)
        }
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
        cryptoDbEmf().transaction { em ->
            em.persist(entity)
        }
        cryptoDbEmf().use { em ->
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
        val fullKeyId = keyPair.public.fullId()
        val entity = SigningKeyEntity(
            tenantId = tenantId,
            keyId = keyId,
            fullKeyId = fullKeyId,
            timestamp = Instant.now(),
            category = CryptoConsts.Categories.LEDGER,
            schemeCodeName = EDDSA_ED25519_CODE_NAME,
            publicKey = keyPair.public.encoded,
            keyMaterial = keyPair.private.encoded,
            encodingVersion = 11,
            masterKeyAlias = UUID.randomUUID().toString(),
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            externalId = UUID.randomUUID().toString(),
            hsmId = UUID.randomUUID().toString(),
            status = SigningKeyEntityStatus.NORMAL
        )
        cryptoDbEmf().transaction { em ->
            em.persist(entity)
        }
        cryptoDbEmf().use { em ->
            val retrieved = em.find(
                SigningKeyEntity::class.java, SigningKeyEntityPrimaryKey(
                    tenantId = tenantId,
                    keyId = keyId
                )
            )
            assertNotNull(retrieved)
            assertEquals(entity.tenantId, retrieved.tenantId)
            assertEquals(entity.keyId, retrieved.keyId)
            assertEquals(entity.fullKeyId, retrieved.fullKeyId)
            assertEquals(entity.timestamp.epochSecond, retrieved.timestamp.epochSecond)
            assertEquals(entity.category, retrieved.category)
            assertEquals(entity.schemeCodeName, retrieved.schemeCodeName)
            assertArrayEquals(entity.publicKey, retrieved.publicKey)
            assertArrayEquals(entity.keyMaterial, retrieved.keyMaterial)
            assertEquals(entity.encodingVersion, retrieved.encodingVersion)
            assertEquals(entity.masterKeyAlias, retrieved.masterKeyAlias)
            assertEquals(entity.alias, retrieved.alias)
            assertEquals(entity.hsmAlias, retrieved.hsmAlias)
            assertEquals(entity.externalId, retrieved.externalId)
            assertEquals(entity.hsmId, retrieved.hsmId)
        }
    }

    @Test
    fun `Should persist and retrieve raw HSM related entities`() {
        val tenantId = randomTenantId()
        val hsmId = UUID.randomUUID().toString()
        val associationId = UUID.randomUUID().toString()
        val categoryAssociationId = UUID.randomUUID().toString()
        val association = HSMAssociationEntity(
            id = associationId,
            tenantId = tenantId,
            hsmId = hsmId,
            timestamp = Instant.now(),
            masterKeyAlias = toHex(UUID.randomUUID().toString().toByteArray()).take(30)
        )
        cryptoDbEmf().transaction { em ->
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
        cryptoDbEmf().transaction { em ->
            em.persist(categoryAssociation)
        }
        cryptoDbEmf().use { em ->
            val retrieved = em.find(HSMCategoryAssociationEntity::class.java, categoryAssociationId)
            assertNotNull(retrieved)
            assertNotSame(categoryAssociation, retrieved)
            assertEquals(categoryAssociationId, retrieved.id)
            assertEquals(CryptoConsts.Categories.LEDGER, retrieved.category)
            assertNotSame(association, retrieved.hsmAssociation)
            assertEquals(associationId, retrieved.hsmAssociation.id)
            assertEquals(tenantId, retrieved.hsmAssociation.tenantId)
            assertEquals(association.masterKeyAlias, retrieved.hsmAssociation.masterKeyAlias)
        }
    }

    @Test
    fun `Should fail to save HSMAssociationEntity with duplicate tenant and work set id`() {
        val tenantId = randomTenantId()
        val hsmId = UUID.randomUUID().toString()
        val association1 = HSMAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            hsmId = hsmId,
            timestamp = Instant.now(),
            masterKeyAlias = toHex(UUID.randomUUID().toString().toByteArray()).take(30)
        )
        cryptoDbEmf().transaction { em ->
            em.persist(association1)
        }
        val association2 = HSMAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            hsmId = hsmId,
            timestamp = Instant.now(),
            masterKeyAlias = toHex(UUID.randomUUID().toString().toByteArray()).take(30)
        )
        assertThrows(PersistenceException::class.java) {
            cryptoDbEmf().transaction { em ->
                em.persist(association2)
            }
        }
    }

    /**
     * As the category association can be changed over time the unique index is defined as
     * "tenant_id, category, deprecated_at" to allow reassignment back to original, e.g. like
     * "T1,LEDGER,WS1" -> "T1,LEDGER,WS2" -> "T1,LEDGER,WS1"
     * Uniqueness of "tenant_id, category, deprecated_at" gives ability to have
     * ONLY one active (where deprecated_at=0) association
     */
    @Test
    fun `Should not fail to save HSMCategoryAssociationEntity with duplicate category and hsm association`() {
        val tenantId = randomTenantId()
        val categoryAssociation1 = createAndPersistHSMCategoryAssociationEntity(
            tenantId,
            CryptoConsts.Categories.LEDGER,
            MasterKeyPolicy.UNIQUE
        )
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            category = categoryAssociation1.category,
            hsmAssociation = categoryAssociation1.hsmAssociation,
            timestamp = Instant.now(),
            deprecatedAt = Instant.now().toEpochMilli()
        )
        cryptoDbEmf().transaction { em ->
            em.persist(categoryAssociation)
        }
    }

    @Test
    fun `Should fail to save HSMCategoryAssociationEntity with duplicate tenant, category and deprecation`() {
        val tenantId = randomTenantId()
        val categoryAssociation1 = createAndPersistHSMCategoryAssociationEntity(
            tenantId,
            CryptoConsts.Categories.LEDGER,
            MasterKeyPolicy.UNIQUE
        )
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            category = categoryAssociation1.category,
            hsmAssociation = categoryAssociation1.hsmAssociation,
            timestamp = Instant.now(),
            deprecatedAt = 0
        )
        assertThrows(PersistenceException::class.java) {
            cryptoDbEmf().transaction { em ->
                em.persist(categoryAssociation)
            }
        }
    }

    @Test
    fun `findTenantAssociation(tenantId, category) should return only active associations`() {
        val tenantId = randomTenantId()
        val deprecatedAssociation = createAndPersistHSMCategoryAssociationEntity(
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            masterKeyPolicy = MasterKeyPolicy.UNIQUE,
            deprecatedAt = Instant.now().toEpochMilli()
        )
        assertNull(hsmStore.findTenantAssociation(tenantId, CryptoConsts.Categories.LEDGER))
        val activeAssociation1 = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            hsmAssociation = deprecatedAssociation.hsmAssociation,
            timestamp = Instant.now(),
            deprecatedAt = 0
        )
        cryptoDbEmf().transaction { em ->
            em.persist(activeAssociation1)
        }
        val result1 = hsmStore.findTenantAssociation(tenantId, CryptoConsts.Categories.LEDGER)
        assertAssociation(activeAssociation1, result1)
        val activeAssociation2 = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            category = CryptoConsts.Categories.TLS,
            hsmAssociation = deprecatedAssociation.hsmAssociation,
            timestamp = Instant.now(),
            deprecatedAt = 0
        )
        cryptoDbEmf().transaction { em ->
            em.persist(activeAssociation2)
        }
        val result2 = hsmStore.findTenantAssociation(tenantId, CryptoConsts.Categories.TLS)
        assertAssociation(activeAssociation2, result2)
    }

    @Test
    fun `findTenantAssociation(tenantId, category) should return null when arguments are not matching`() {
        val tenantId = randomTenantId()
        createAndPersistHSMCategoryAssociationEntity(tenantId, CryptoConsts.Categories.LEDGER, MasterKeyPolicy.UNIQUE)
        assertNull(
            hsmStore.findTenantAssociation(tenantId, CryptoConsts.Categories.SESSION_INIT)
        )
        assertNull(
            hsmStore.findTenantAssociation(randomTenantId(), CryptoConsts.Categories.LEDGER)
        )
    }


    @Test
    fun `Should associate tenants to categories and then retrieve them and get HSM usage`() {
        val hsmId1 = UUID.randomUUID().toString()
        val hsmId2 = UUID.randomUUID().toString()
        val tenantId1 = randomTenantId()
        val tenantId2 = randomTenantId()
        val tenantId3 = randomTenantId()
        val tenantId4 = randomTenantId()
        val tenantId5 = randomTenantId()
        hsmStore.associate(tenantId1, CryptoConsts.Categories.LEDGER, hsmId1, MasterKeyPolicy.NONE)
        hsmStore.associate(tenantId2, CryptoConsts.Categories.LEDGER, hsmId2, MasterKeyPolicy.UNIQUE)
        hsmStore.associate(tenantId3, CryptoConsts.Categories.LEDGER, hsmId2, MasterKeyPolicy.NONE)
        hsmStore.associate(tenantId4, CryptoConsts.Categories.TLS, hsmId2, MasterKeyPolicy.NONE)
        hsmStore.associate(tenantId5, CryptoConsts.Categories.TLS, hsmId1, MasterKeyPolicy.NONE)
        val usages = hsmStore.getHSMUsage()
        assertTrue(usages.size >= 2)
        assertEquals(2, usages.first { it.hsmId == hsmId1 }.usages)
        assertEquals(3, usages.first { it.hsmId == hsmId2 }.usages)

        val association1 = hsmStore.findTenantAssociation(tenantId1, CryptoConsts.Categories.LEDGER)
        assertNotNull(association1)
        assertEquals(tenantId1, association1!!.tenantId)
        assertEquals(CryptoConsts.Categories.LEDGER, association1.category)
        assertEquals(0, association1.deprecatedAt)
        assertEquals(hsmId1, association1.hsmId)
        assertNull(association1.masterKeyAlias)

        val association2 = hsmStore.findTenantAssociation(tenantId2, CryptoConsts.Categories.LEDGER)
        assertNotNull(association2)
        assertEquals(tenantId2, association2!!.tenantId)
        assertEquals(CryptoConsts.Categories.LEDGER, association2.category)
        assertEquals(0, association2.deprecatedAt)
        assertEquals(hsmId2, association2.hsmId)
        assertNotNull(association2.masterKeyAlias)
    }

    @Test
    fun `Should save and then retrieve wrapping keys`() {
        val masterKey = WrappingKey.generateWrappingKey(schemeMetadata)
        val alias1 = UUID.randomUUID().toString()
        val alias2 = UUID.randomUUID().toString()
        val key1 = WrappingKey.generateWrappingKey(schemeMetadata)
        val wrappingKeyInfo1 = WrappingKeyInfo(
            encodingVersion = 1,
            algorithmName = key1.algorithm,
            keyMaterial = masterKey.wrap(key1)
        )
        val key2 = WrappingKey.generateWrappingKey(schemeMetadata)
        val wrappingKeyInfo2 = WrappingKeyInfo(
            encodingVersion = 1,
            algorithmName = key2.algorithm,
            keyMaterial = masterKey.wrap(key2)
        )
        wrappingKeyStore.saveWrappingKey(alias1, wrappingKeyInfo1)
        wrappingKeyStore.saveWrappingKey(alias2, wrappingKeyInfo2)
        assertEquals(key1, masterKey.unwrapWrappingKey(wrappingKeyStore.findWrappingKey(alias1)!!.keyMaterial))
        assertEquals(key2, masterKey.unwrapWrappingKey(wrappingKeyStore.findWrappingKey(alias2)!!.keyMaterial))
    }

    @Test
    fun `Should fail override existing wrapping keys`() {
        val masterKey = WrappingKey.generateWrappingKey(schemeMetadata)
        val alias = UUID.randomUUID().toString()
        val key1 = WrappingKey.generateWrappingKey(schemeMetadata)
        val wrappingKeyInfo1 = WrappingKeyInfo(
            encodingVersion = 1,
            algorithmName = key1.algorithm,
            keyMaterial = masterKey.wrap(key1)
        )
        val key2 = WrappingKey.generateWrappingKey(schemeMetadata)
        val wrappingKeyInfo2 = WrappingKeyInfo(
            encodingVersion = 1,
            algorithmName = key2.algorithm,
            keyMaterial = masterKey.wrap(key2)
        )
        wrappingKeyStore.saveWrappingKey(alias, wrappingKeyInfo1)
        assertEquals(key1, masterKey.unwrapWrappingKey(wrappingKeyStore.findWrappingKey(alias)!!.keyMaterial))
        assertThrows(PersistenceException::class.java) {
            wrappingKeyStore.saveWrappingKey(alias, wrappingKeyInfo2)
        }
        assertEquals(key1, masterKey.unwrapWrappingKey(wrappingKeyStore.findWrappingKey(alias)!!.keyMaterial))
    }

    @ParameterizedTest
    @MethodSource("signingTenants")
    fun `Should fail saving same public key`(tenantId: String) {
        val hsmId = UUID.randomUUID().toString()
        val p1 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(hsmId, EDDSA_ED25519_CODE_NAME)
        signingKeyStore.save(tenantId, p1)
        assertThrows(PersistenceException::class.java) {
            signingKeyStore.save(tenantId, p1)
        }
        signingKeyStore.save(tenantId, w1)
        assertThrows(PersistenceException::class.java) {
            signingKeyStore.save(tenantId, w1)
        }
    }

    @Test
    fun `Should save same public keys for different tenants and lookup by id for each tenant`() {
        val hsmId = UUID.randomUUID().toString()
        val tenantId1 = CryptoTenants.P2P
        val tenantId2 = CryptoTenants.RPC_API
        val p1 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(hsmId, EDDSA_ED25519_CODE_NAME)
        signingKeyStore.save(tenantId1, p1)
        signingKeyStore.save(tenantId2, p1)
        signingKeyStore.save(tenantId1, w1)
        signingKeyStore.save(tenantId2, w1)
        val keyP11 = signingKeyStore.lookup(tenantId1, listOf(p1.key.publicKey.publicKeyId()))
        assertEquals(1, keyP11.size)
        assertSigningCachedKey(tenantId1, p1, keyP11.first())
        val keyP12 = signingKeyStore.lookup(tenantId2, listOf(p1.key.publicKey.publicKeyId()))
        assertEquals(1, keyP12.size)
        assertSigningCachedKey(tenantId2, p1, keyP12.first())
        val keyW11 = signingKeyStore.lookup(tenantId1, listOf(w1.key.publicKey.publicKeyId()))
        assertEquals(1, keyW11.size)
        assertSigningCachedKey(tenantId1, w1, keyW11.first())
        val keyW12 = signingKeyStore.lookup(tenantId2, listOf(w1.key.publicKey.publicKeyId()))
        assertEquals(1, keyW12.size)
        assertSigningCachedKey(tenantId2, w1, keyW12.first())
    }

    @ParameterizedTest
    @MethodSource("signingTenants")
    fun `Should find existing signing keys by alias`(tenantId: String) {
        val hsmId = UUID.randomUUID().toString()
        val p1 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.TLS, ECDSA_SECP256R1_CODE_NAME)
        signingKeyStore.save(tenantId, p1)
        signingKeyStore.save(tenantId, p2)
        assertNull(signingKeyStore.find(tenantId, UUID.randomUUID().toString()))
        assertSigningCachedKey(tenantId, p1, signingKeyStore.find(tenantId, p1.alias!!))
        assertSigningCachedKey(tenantId, p2, signingKeyStore.find(tenantId, p2.alias!!))
        assertNull(signingKeyStore.find(tenantId, UUID.randomUUID().toString()))
    }

    @ParameterizedTest
    @MethodSource("signingTenants")
    fun `Should looup existing signing keys by ids`(tenantId: String) {
        val hsmId = UUID.randomUUID().toString()
        val p0 = generateKeyPair(EDDSA_ED25519_CODE_NAME).public
        val p1 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.SESSION_INIT, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(hsmId, EDDSA_ED25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(hsmId, ECDSA_SECP256R1_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(hsmId, ECDSA_SECP256R1_CODE_NAME)
        signingKeyStore.save(tenantId, p1)
        signingKeyStore.save(tenantId, p2)
        signingKeyStore.save(tenantId, p3)
        signingKeyStore.save(tenantId, p4)
        signingKeyStore.save(tenantId, w1)
        signingKeyStore.save(tenantId, w2)
        signingKeyStore.save(tenantId, w3)
        val keys = signingKeyStore.lookup(
            tenantId,
            listOf(
                p1.key.publicKey.publicKeyId(),
                p0.publicKeyId(),
                p3.key.publicKey.publicKeyId(),
                w2.key.publicKey.publicKeyId()
            )
        )
        assertEquals(3, keys.size)
        assertSigningCachedKey(tenantId, p1, keys.firstOrNull { it.id == p1.key.publicKey.publicKeyId() })
        assertSigningCachedKey(tenantId, p3, keys.firstOrNull { it.id == p3.key.publicKey.publicKeyId() })
        assertSigningCachedKey(tenantId, w2, keys.firstOrNull { it.id == w2.key.publicKey.publicKeyId() })
    }

    @ParameterizedTest
    @MethodSource("signingTenants")
    fun `Should find existing signing keys by public keys`(tenantId: String) {
        val hsmId = UUID.randomUUID().toString()
        val p1 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(hsmId, EDDSA_ED25519_CODE_NAME)
        signingKeyStore.save(tenantId, p1)
        signingKeyStore.save(tenantId, w1)
        assertNull(signingKeyStore.find(tenantId, generateKeyPair(EDDSA_ED25519_CODE_NAME).public))
        assertSigningCachedKey(tenantId, p1, signingKeyStore.find(tenantId, p1.key.publicKey))
        assertSigningCachedKey(tenantId, w1, signingKeyStore.find(tenantId, w1.key.publicKey))
    }

    /**
     * The test does post lookup filtering to ensure that only keys generated by this test are considered.
     */
    @ParameterizedTest
    @MethodSource("signingTenants")
    fun `Should do various lookups for signing keys`(tenantId: String) {
        val hsmId = UUID.randomUUID().toString()
        val p1 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val p2 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.TLS, ECDSA_SECP256R1_CODE_NAME)
        val p3 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.SESSION_INIT, EDDSA_ED25519_CODE_NAME)
        val p4 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.LEDGER, EDDSA_ED25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(hsmId, EDDSA_ED25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(hsmId, ECDSA_SECP256R1_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(hsmId, ECDSA_SECP256R1_CODE_NAME)
        val thisTestKeys = setOf(
            p1.key.publicKey.publicKeyId(),
            p2.key.publicKey.publicKeyId(),
            p3.key.publicKey.publicKeyId(),
            p4.key.publicKey.publicKeyId(),
            w1.key.publicKey.publicKeyId(),
            w2.key.publicKey.publicKeyId(),
            w3.key.publicKey.publicKeyId()
        )
        signingKeyStore.save(tenantId, p1)
        signingKeyStore.save(tenantId, p2)
        signingKeyStore.save(tenantId, p3)
        signingKeyStore.save(tenantId, p4)
        signingKeyStore.save(tenantId, w1)
        signingKeyStore.save(tenantId, w2)
        signingKeyStore.save(tenantId, w3)
        val result1 = signingKeyStore.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 10,
            SigningKeyOrderBy.ALIAS,
            mapOf(
                CATEGORY_FILTER to CryptoConsts.Categories.LEDGER
            )
        ).filter { thisTestKeys.contains(it.id) }
        assertEquals(2, result1.size)
        listOf(p1, p4).sortedBy { it.alias }.forEachIndexed { i, o ->
            assertSigningCachedKey(tenantId, o, result1.elementAt(i))
        }
        val result2 = signingKeyStore.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 10,
            SigningKeyOrderBy.ALIAS_DESC,
            mapOf(
                CATEGORY_FILTER to CryptoConsts.Categories.LEDGER
            )
        ).filter { thisTestKeys.contains(it.id) }
        assertEquals(2, result2.size)
        listOf(p1, p4).sortedByDescending { it.alias }.forEachIndexed { i, o ->
            assertSigningCachedKey(tenantId, o, result2.elementAt(i))
        }
        val result3 = signingKeyStore.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 10,
            SigningKeyOrderBy.ALIAS_DESC,
            mapOf(
                CATEGORY_FILTER to CryptoConsts.Categories.CI,
                SCHEME_CODE_NAME_FILTER to EDDSA_ED25519_CODE_NAME
            )
        ).filter { thisTestKeys.contains(it.id) }
        assertEquals(1, result3.size)
        assertSigningCachedKey(tenantId, w1, result3.first())
        val result4 = signingKeyStore.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 10,
            SigningKeyOrderBy.NONE,
            mapOf(
                ALIAS_FILTER to p2.alias!!,
                SCHEME_CODE_NAME_FILTER to ECDSA_SECP256R1_CODE_NAME,
                CATEGORY_FILTER to CryptoConsts.Categories.TLS
            )
        ).filter { thisTestKeys.contains(it.id) }
        assertEquals(1, result4.size)
        assertSigningCachedKey(tenantId, p2, result4.first())
        val result5 = signingKeyStore.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 10,
            SigningKeyOrderBy.CATEGORY_DESC,
            mapOf(
                MASTER_KEY_ALIAS_FILTER to w3.masterKeyAlias!!,
                SCHEME_CODE_NAME_FILTER to ECDSA_SECP256R1_CODE_NAME,
                CATEGORY_FILTER to CryptoConsts.Categories.CI,
                EXTERNAL_ID_FILTER to w3.externalId!!
            )
        ).filter { thisTestKeys.contains(it.id) }
        assertEquals(1, result5.size)
        assertSigningCachedKey(tenantId, w3, result5.first())
        val result6 = signingKeyStore.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 20,
            SigningKeyOrderBy.ID,
            mapOf(
                CREATED_AFTER_FILTER to Instant.now().minusSeconds(300).toString(),
                CREATED_BEFORE_FILTER to Instant.now().minusSeconds(-1).toString()
            )
        ).filter { thisTestKeys.contains(it.id) }
        assertEquals(7, result6.size)
        listOf(p1, p2, p3, p4, w1, w2, w3).sortedBy {
            when (it) {
                is SigningPublicKeySaveContext -> it.key.publicKey.publicKeyId()
                is SigningWrappedKeySaveContext -> it.key.publicKey.publicKeyId()
                else -> throw IllegalArgumentException()
            }
        }.forEachIndexed { i, o ->
            when (o) {
                is SigningPublicKeySaveContext -> assertSigningCachedKey(tenantId, o, result6.elementAt(i))
                is SigningWrappedKeySaveContext -> assertSigningCachedKey(tenantId, o, result6.elementAt(i))
                else -> throw IllegalArgumentException()
            }
        }
    }

    /**
     * To ensure test validity the test uses only vnode tenant as its tenant id is unique.
     */
    @Test
    fun `Should do paged lookups for signing keys`() {
        val hsmId = UUID.randomUUID().toString()
        val tenantId = CryptoDBSetup.vNodeHoldingIdentity.shortHash.value
        val p1 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.LEDGER, X25519_CODE_NAME)
        val p2 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.TLS, X25519_CODE_NAME)
        val p3 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.SESSION_INIT, X25519_CODE_NAME)
        val p4 = createSigningKeySaveContext(hsmId, CryptoConsts.Categories.LEDGER, X25519_CODE_NAME)
        val w1 = createSigningWrappedKeySaveContext(hsmId, X25519_CODE_NAME)
        val w2 = createSigningWrappedKeySaveContext(hsmId, X25519_CODE_NAME)
        val w3 = createSigningWrappedKeySaveContext(hsmId, X25519_CODE_NAME)
        signingKeyStore.save(tenantId, p1)
        signingKeyStore.save(tenantId, p2)
        signingKeyStore.save(tenantId, p3)
        signingKeyStore.save(tenantId, p4)
        signingKeyStore.save(tenantId, w1)
        signingKeyStore.save(tenantId, w2)
        signingKeyStore.save(tenantId, w3)
        val page1 = signingKeyStore.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 4,
            SigningKeyOrderBy.ID,
            mapOf(
                SCHEME_CODE_NAME_FILTER to X25519_CODE_NAME
            )
        )
        assertEquals(4, page1.size)
        listOf(p1, p2, p3, p4, w1, w2, w3).sortedBy {
            when (it) {
                is SigningPublicKeySaveContext -> it.key.publicKey.publicKeyId()
                is SigningWrappedKeySaveContext -> it.key.publicKey.publicKeyId()
                else -> throw IllegalArgumentException()
            }
        }.drop(0).take(4).forEachIndexed { i, o ->
            when (o) {
                is SigningPublicKeySaveContext -> assertSigningCachedKey(tenantId, o, page1.elementAt(i))
                is SigningWrappedKeySaveContext -> assertSigningCachedKey(tenantId, o, page1.elementAt(i))
                else -> throw IllegalArgumentException()
            }
        }
        val page2 = signingKeyStore.lookup(
            tenantId = tenantId,
            skip = 4,
            take = 4,
            SigningKeyOrderBy.ID,
            mapOf(
                SCHEME_CODE_NAME_FILTER to X25519_CODE_NAME
            )
        )
        assertEquals(3, page2.size)
        listOf(p1, p2, p3, p4, w1, w2, w3).sortedBy {
            when (it) {
                is SigningPublicKeySaveContext -> it.key.publicKey.publicKeyId()
                is SigningWrappedKeySaveContext -> it.key.publicKey.publicKeyId()
                else -> throw IllegalArgumentException()
            }
        }.drop(4).take(3).forEachIndexed { i, o ->
            when (o) {
                is SigningPublicKeySaveContext -> assertSigningCachedKey(tenantId, o, page2.elementAt(i))
                is SigningWrappedKeySaveContext -> assertSigningCachedKey(tenantId, o, page2.elementAt(i))
                else -> throw IllegalArgumentException()
            }
        }
        val page3 = signingKeyStore.lookup(
            tenantId = tenantId,
            skip = 7,
            take = 4,
            SigningKeyOrderBy.ID,
            mapOf(
                SCHEME_CODE_NAME_FILTER to X25519_CODE_NAME
            )
        )
        assertEquals(0, page3.size)
    }
}
