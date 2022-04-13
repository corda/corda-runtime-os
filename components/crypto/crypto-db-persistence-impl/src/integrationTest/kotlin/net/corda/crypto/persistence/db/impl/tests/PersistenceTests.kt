package net.corda.crypto.persistence.db.impl.tests

import com.typesafe.config.ConfigFactory
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.EXTERNAL_ID_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.db.impl.signing.SigningKeyCacheImpl
import net.corda.crypto.persistence.db.impl.soft.SoftCryptoKeyCacheImpl
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityPrimaryKey
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
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
import net.corda.test.util.LoggingUtils.emphasise
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.io.StringWriter
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
        private val logger = contextLogger()

        @InjectService(timeout = 5000)
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService(timeout = 5000)
        lateinit var lbm: LiquibaseSchemaMigrator

        @InjectService(timeout = 5000)
        lateinit var layeredPropertyMapFactory: LayeredPropertyMapFactory

        @InjectService(timeout = 5000)
        lateinit var schemeMetadata: CipherSchemeMetadata

        private val configFactory = SmartConfigFactory.create(
            ConfigFactory.parseString(
                """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=key
            ${SmartConfigFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            )
        )

        private lateinit var cryptoEmf: EntityManagerFactory

        private val cryptoDbConfig: EntityManagerConfiguration =
            DbUtils.getEntityManagerConfiguration("crypto")

        @JvmStatic
        @BeforeAll
        fun setup() {
            setupCryptoEntities()
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            if (this::cryptoEmf.isInitialized) {
                cryptoEmf.close()
            }

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

        private fun randomTenantId() = publicKeyIdOf(UUID.randomUUID().toString().toByteArray())

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
        val keyPair = generateKeyPair(EDDSA_ED25519_CODE_NAME)
        val tenantId = randomTenantId()
        val keyId = publicKeyIdOf(keyPair.public)
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

    @Test
    fun `Should be able to cache and then retrieve repeatedly wrapping keys`() {
        val cache = SoftCryptoKeyCacheImpl(
            config = configFactory.create(ConfigFactory.empty()),
            entityManagerFactory = cryptoEmf,
            masterKey = WrappingKey.createWrappingKey(schemeMetadata)
        )
        val alias1 = UUID.randomUUID().toString()
        val newKey1 = WrappingKey.createWrappingKey(schemeMetadata)
        cache.act {
            it.saveWrappingKey(alias1, newKey1, false)
            val cached = it.findWrappingKey(alias1)
            assertNotNull(cached)
            assertEquals(newKey1, cached)
        }
        val alias2 = UUID.randomUUID().toString()
        val newKey2 = WrappingKey.createWrappingKey(schemeMetadata)
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
            config = configFactory.create(ConfigFactory.empty()),
            entityManagerFactory = cryptoEmf,
            masterKey = WrappingKey.createWrappingKey(schemeMetadata)
        )
        val alias = UUID.randomUUID().toString()
        val newKey1 = WrappingKey.createWrappingKey(schemeMetadata)
        cache.act {
            it.saveWrappingKey(alias, newKey1, true)
            val cached = it.findWrappingKey(alias)
            assertNotNull(cached)
            assertEquals(newKey1, cached)
        }
        val newKey2 = WrappingKey.createWrappingKey(schemeMetadata)
        assertThrows(PersistenceException::class.java) {
            cache.act {
                it.saveWrappingKey(alias, newKey2, true)
            }
        }
    }

    @Test
    fun `Should not override existing wrapping key with same alias when failIfExists equals false`() {
        val cache = SoftCryptoKeyCacheImpl(
            config = configFactory.create(ConfigFactory.empty()),
            entityManagerFactory = cryptoEmf,
            masterKey = WrappingKey.createWrappingKey(schemeMetadata)
        )
        val alias = UUID.randomUUID().toString()
        val newKey1 = WrappingKey.createWrappingKey(schemeMetadata)
        cache.act {
            it.saveWrappingKey(alias, newKey1, false)
            val cached = it.findWrappingKey(alias)
            assertNotNull(cached)
            assertEquals(newKey1, cached)
        }
        val newKey2 = WrappingKey.createWrappingKey(schemeMetadata)
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
            it.lookup(listOf(publicKeyIdOf(p1.key.publicKey)))
        }
        assertEquals(1, keyP11.size)
        assertEquals(tenantId1, p1, keyP11.first())
        val keyP12 = cache.act(tenantId2) {
            it.lookup(listOf(publicKeyIdOf(p1.key.publicKey)))
        }
        assertEquals(1, keyP12.size)
        assertEquals(tenantId2, p1, keyP12.first())
        val keyW11 = cache.act(tenantId1) {
            it.lookup(listOf(publicKeyIdOf(w1.key.publicKey)))
        }
        assertEquals(1, keyW11.size)
        assertEquals(tenantId1, w1, keyW11.first())
        val keyW12 = cache.act(tenantId2) {
            it.lookup(listOf(publicKeyIdOf(w1.key.publicKey)))
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
                    publicKeyIdOf(p1.key.publicKey),
                    publicKeyIdOf(p12.key.publicKey),
                    publicKeyIdOf(p3.key.publicKey),
                    publicKeyIdOf(w2.key.publicKey)
                )
            )
        }
        assertEquals(3, keys.size)
        assertEquals(tenantId1, p1, keys.firstOrNull { it.id == publicKeyIdOf(p1.key.publicKey) })
        assertEquals(tenantId1, p3, keys.firstOrNull { it.id == publicKeyIdOf(p3.key.publicKey) })
        assertEquals(tenantId1, w2, keys.firstOrNull { it.id == publicKeyIdOf(w2.key.publicKey) })
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
            when(it) {
                is SigningPublicKeySaveContext -> publicKeyIdOf(it.key.publicKey)
                is SigningWrappedKeySaveContext -> publicKeyIdOf(it.key.publicKey)
                else -> throw IllegalArgumentException()
            }
        }.forEachIndexed { i, o ->
            when(o) {
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
            when(it) {
                is SigningPublicKeySaveContext -> publicKeyIdOf(it.key.publicKey)
                is SigningWrappedKeySaveContext -> publicKeyIdOf(it.key.publicKey)
                else -> throw IllegalArgumentException()
            }
        }.drop(0).take(2).forEachIndexed { i, o ->
            when(o) {
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
            when(it) {
                is SigningPublicKeySaveContext -> publicKeyIdOf(it.key.publicKey)
                is SigningWrappedKeySaveContext -> publicKeyIdOf(it.key.publicKey)
                else -> throw IllegalArgumentException()
            }
        }.drop(2).take(2).forEachIndexed { i, o ->
            when(o) {
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
        assertEquals(publicKeyIdOf(expected.key.publicKey), actual!!.id)
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
        assertEquals(publicKeyIdOf(expected.key.publicKey), actual!!.id)
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
        config = configFactory.create(ConfigFactory.empty()),
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