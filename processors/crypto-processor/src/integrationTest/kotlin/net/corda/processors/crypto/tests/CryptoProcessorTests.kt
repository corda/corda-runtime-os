package net.corda.processors.crypto.tests

import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.data.config.Configuration
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.core.DbPrivilege
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.TestDbInfo
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.processors.crypto.CryptoProcessor
import net.corda.processors.crypto.tests.infra.FlowOpsResponses
import net.corda.processors.crypto.tests.infra.RESPONSE_TOPIC
import net.corda.processors.crypto.tests.infra.DependenciesTracker
import net.corda.processors.crypto.tests.infra.makeBootstrapConfig
import net.corda.processors.crypto.tests.infra.makeClientId
import net.corda.processors.crypto.tests.infra.makeMessagingConfig
import net.corda.processors.crypto.tests.infra.randomDataByteArray
import net.corda.processors.crypto.tests.infra.randomTenantId
import net.corda.processors.crypto.tests.infra.startAndWait
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Crypto.Companion.FLOW_OPS_MESSAGE_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.publicKeyId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.stream.Stream
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class, DBSetup::class)
class CryptoProcessorTests {
    companion object {
        private val CLIENT_ID = makeClientId<CryptoProcessorTests>()

        @InjectService(timeout = 5000L)
        lateinit var lifecycleRegistry: LifecycleRegistry

        @InjectService(timeout = 5000L)
        lateinit var coordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 5000L)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 5000L)
        lateinit var subscriptionFactory: SubscriptionFactory

        @InjectService(timeout = 5000L)
        lateinit var cryptoProcessor: CryptoProcessor

        @InjectService(timeout = 5000L)
        lateinit var opsClient: CryptoOpsClient

        @InjectService(timeout = 5000L)
        lateinit var verifier: SignatureVerificationService

        @InjectService(timeout = 5000L)
        lateinit var keyEncodingService: KeyEncodingService

        @InjectService(timeout = 5000)
        lateinit var entitiesRegistry: JpaEntitiesRegistry

        @InjectService(timeout = 5000)
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService(timeout = 5000L)
        lateinit var hsmRegistrationClient: HSMRegistrationClient

        @InjectService(timeout = 5000)
        lateinit var lbm: LiquibaseSchemaMigrator

        private lateinit var publisher: Publisher

        private lateinit var flowOpsResponses: FlowOpsResponses

        private lateinit var transformer: CryptoFlowOpsTransformer

        private val vnodeId: String = randomTenantId()

        private val clusterDb = TestDbInfo.createConfig()

        private val cryptoDb = TestDbInfo(
            name = CordaDb.Crypto.persistenceUnitName,
            schemaName = DbSchema.CRYPTO
        )

        private val vnodeDb = TestDbInfo(
            name = "vnode_crypto_$vnodeId",
            schemaName = "vnode_crypto"
        )

        private val boostrapConfig = makeBootstrapConfig(
            mapOf(
                ConfigKeys.DB_CONFIG to clusterDb.config
            )
        )

        private val messagingConfig = makeMessagingConfig(boostrapConfig)

        @JvmStatic
        @BeforeAll
        fun setup() {
            setupPrerequisites()
            setupDatabases()
            startDependencies()
            assignHSMs()
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            if (::flowOpsResponses.isInitialized) {
                flowOpsResponses.close()
            }
        }

        private fun setupPrerequisites() {
            flowOpsResponses = FlowOpsResponses(messagingConfig, subscriptionFactory)
            transformer = CryptoFlowOpsTransformer(
                requestingComponent = "test",
                responseTopic = RESPONSE_TOPIC,
                keyEncodingService = keyEncodingService
            )
            publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), messagingConfig)
            publisher.publish(
                listOf(
                    Record(
                        CONFIG_TOPIC,
                        MESSAGING_CONFIG,
                        Configuration(messagingConfig.root().render(), "1")
                    )
                )
            )
        }

        private fun setupDatabases() {
            val databaseInstaller = DatabaseInstaller(entityManagerFactoryFactory, lbm, entitiesRegistry)
            val configEmf = databaseInstaller.setupClusterDatabase(
                clusterDb,
                "config",
                ConfigurationEntities.classes
            )
            databaseInstaller.setupDatabase(
                cryptoDb,
                "crypto"
            ).close()
            databaseInstaller.setupDatabase(
                vnodeDb,
                "vnode-crypto",
                CryptoEntities.classes
            ).close()
            addDbConnectionConfigs(configEmf, cryptoDb, vnodeDb)
            configEmf.close()
        }

        private fun addDbConnectionConfigs(configEmf: EntityManagerFactory, vararg dbs: TestDbInfo) {
            dbs.forEach { db ->
                val configAsString = db.config.root().render(ConfigRenderOptions.concise())
                configEmf.transaction {
                    val existing = it.createQuery("""
                        SELECT c FROM DbConnectionConfig c WHERE c.name=:name AND c.privilege=:privilege)
                    """.trimIndent())
                        .setParameter("name", db.name)
                        .setParameter("privilege", DbPrivilege.DML)
                        .resultList
                    if(existing.isEmpty()) {
                        val record = DbConnectionConfig(
                            UUID.randomUUID(),
                            db.name,
                            DbPrivilege.DML,
                            Instant.now(),
                            "sa",
                            "Test ${db.name}",
                            configAsString
                        )
                        it.persist(record)
                    }
                }
            }
        }

        private fun startDependencies() {
            hsmRegistrationClient.startAndWait()
            cryptoProcessor.startAndWait(boostrapConfig)
            val tracker = DependenciesTracker(
                LifecycleCoordinatorName.forComponent<CryptoProcessorTests>(),
                coordinatorFactory,
                lifecycleRegistry,
                setOf(
                    LifecycleCoordinatorName.forComponent<CryptoProcessor>(),
                    LifecycleCoordinatorName.forComponent<HSMRegistrationClient>()
                )
            ).also { it.startAndWait() }
            tracker.waitUntilAllUp(Duration.ofSeconds(60))
            tracker.stop()
        }

        private fun assignHSMs() {
            CryptoConsts.Categories.all().forEach {
                // cluster is assigned in the crypto processor
                hsmRegistrationClient.assignSoftHSM(vnodeId, it)
            }
        }

        @JvmStatic
        fun testCategories(): Stream<Arguments> = Stream.of(
            Arguments.of(CryptoConsts.Categories.LEDGER, vnodeId),
            Arguments.of(CryptoConsts.Categories.TLS, vnodeId),
            Arguments.of(CryptoConsts.Categories.SESSION, vnodeId),
            Arguments.of(CryptoConsts.Categories.LEDGER, CryptoConsts.CLUSTER_TENANT_ID),
            Arguments.of(CryptoConsts.Categories.TLS, CryptoConsts.CLUSTER_TENANT_ID),
            Arguments.of(CryptoConsts.Categories.SESSION, CryptoConsts.CLUSTER_TENANT_ID)
        )

        @JvmStatic
        fun testTenants(): Stream<Arguments> = Stream.of(
            Arguments.of(vnodeId),
            Arguments.of(CryptoConsts.CLUSTER_TENANT_ID),
        )
    }

    @ParameterizedTest
    @MethodSource("testCategories")
    fun `Should be able to get supported schemes`(
        category: String,
        tenantId: String
    ) {
        val supportedSchemes = opsClient.getSupportedSchemes(tenantId, category)
        assertTrue(supportedSchemes.isNotEmpty())
    }

    @ParameterizedTest
    @MethodSource("testTenants")
    fun `Should not find unknown public key by its id`(
        tenantId: String
    ) {
        val found = opsClient.lookup(
            tenantId = tenantId,
            ids = listOf(publicKeyIdFromBytes(UUID.randomUUID().toString().toByteArray()))
        )
        assertEquals(0, found.size)
    }

    @ParameterizedTest
    @MethodSource("testTenants")
    fun `Should return empty collection when lookp filter does not match`(
        tenantId: String
    ) {
        val found = opsClient.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 20,
            orderBy = CryptoKeyOrderBy.NONE,
            filter = mapOf(
                CryptoConsts.SigningKeyFilters.ALIAS_FILTER to UUID.randomUUID().toString()
            )
        )
        assertEquals(0, found.size)
    }

    @ParameterizedTest
    @MethodSource("testCategories")
    fun `Should generate a new key pair using alias then find it and use for signing`(
        category: String,
        tenantId: String
    ) {
        val alias = UUID.randomUUID().toString()

        val original = opsClient.generateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = alias,
            scheme = ECDSA_SECP256R1_CODE_NAME
        )

        `Should find existing public key by its id`(tenantId, alias, original, category, null)

        `Should find existing public key by its alias`(tenantId, alias, original, category)

        `Should be able to sign`(tenantId, original)

        `Should be able to sign using custom signature spec`(tenantId, original)

        `Should be able to sign by flow ops`(tenantId, original)
    }

    @ParameterizedTest
    @MethodSource("testTenants")
    fun `Should generate a new fresh key pair with external id then find it and use for signing`(
        tenantId: String
    ) {
        val externalId = UUID.randomUUID().toString()

        val original = opsClient.freshKey(
            tenantId = tenantId,
            externalId = externalId,
            scheme = ECDSA_SECP256R1_CODE_NAME,
            context = CryptoOpsClient.EMPTY_CONTEXT
        )

        `Should find existing public key by its id`(
            tenantId,
            null,
            original,
            CryptoConsts.Categories.FRESH_KEYS,
            externalId
        )

        `Should be able to sign`(tenantId, original)

        `Should be able to sign using custom signature spec`(tenantId, original)

        `Should be able to sign by flow ops`(tenantId, original)
    }

    @ParameterizedTest
    @MethodSource("testTenants")
    fun `Should generate a new fresh key pair without external id then find it and use for signing`(
        tenantId: String
    ) {
        val original = opsClient.freshKey(
            tenantId = tenantId,
            scheme = ECDSA_SECP256R1_CODE_NAME,
            context = CryptoOpsClient.EMPTY_CONTEXT
        )

        `Should find existing public key by its id`(
            tenantId,
            null,
            original,
            CryptoConsts.Categories.FRESH_KEYS,
            null
        )

        `Should be able to sign`(tenantId, original)

        `Should be able to sign using custom signature spec`(tenantId, original)

        `Should be able to sign by flow ops`(tenantId, original)
    }

    @ParameterizedTest
    @MethodSource("testTenants")
    fun `Should generate fresh key pair without external id by flow ops then find it and use for signing`(
        tenantId: String
    ) {
        val key = UUID.randomUUID().toString()
        val event = transformer.createFreshKey(
            tenantId = tenantId,
            scheme = RSA_CODE_NAME
        )
        publisher.publish(
            listOf(
                Record(
                    topic = FLOW_OPS_MESSAGE_TOPIC,
                    key = key,
                    value = event
                )
            )
        ).forEach { it.get() }
        val response = flowOpsResponses.waitForResponse(key)
        val original = transformer.transform(response) as PublicKey

        `Should be able to sign`(tenantId, original)

        `Should be able to sign using custom signature spec`(tenantId, original)

        `Should be able to sign by flow ops`(tenantId, original)
    }

    @ParameterizedTest
    @MethodSource("testTenants")
    fun `Should generate fresh key pair with external id by flow ops then find it and use for signing`(
        tenantId: String
    ) {
        val externalId = UUID.randomUUID().toString()
        val key = UUID.randomUUID().toString()
        val event = transformer.createFreshKey(
            tenantId = tenantId,
            scheme = RSA_CODE_NAME,
            externalId = externalId
        )
        publisher.publish(
            listOf(
                Record(
                    topic = FLOW_OPS_MESSAGE_TOPIC,
                    key = key,
                    value = event
                )
            )
        ).forEach { it.get() }
        val response = flowOpsResponses.waitForResponse(key)
        val original = transformer.transform(response) as PublicKey

        `Should be able to sign`(tenantId, original)

        `Should be able to sign using custom signature spec`(tenantId, original)

        `Should be able to sign by flow ops`(tenantId, original)
    }

    private fun `Should find existing public key by its id`(
        tenantId: String,
        alias: String?,
        publicKey: PublicKey,
        category: String,
        externalId: String?
    ) {
        val found = opsClient.lookup(
            tenantId = tenantId,
            ids = listOf(publicKey.publicKeyId())
        )
        assertEquals(1, found.size)
        assertEquals(publicKey.publicKeyId(), found[0].id)
        assertEquals(tenantId, found[0].tenantId)
        if (alias.isNullOrBlank()) {
            assertNull(found[0].alias)
        } else {
            assertEquals(alias, found[0].alias)
        }
        assertEquals(category, found[0].category)
        assertTrue(publicKey.encoded.contentEquals(found[0].publicKey.array()))
        assertNotNull(found[0].schemeCodeName)
        assertNotNull(found[0].masterKeyAlias)
        if (externalId.isNullOrBlank()) {
            assertNull(found[0].externalId)
        } else {
            assertEquals(externalId, found[0].externalId)
        }
        assertNull(found[0].hsmAlias)
    }

    private fun `Should find existing public key by its alias`(
        tenantId: String,
        alias: String,
        publicKey: PublicKey,
        category: String
    ) {
        val found = opsClient.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 20,
            orderBy = CryptoKeyOrderBy.NONE,
            filter = mapOf(
                CryptoConsts.SigningKeyFilters.ALIAS_FILTER to alias
            )
        )
        assertEquals(1, found.size)
        assertEquals(publicKey.publicKeyId(), found[0].id)
        assertEquals(tenantId, found[0].tenantId)
        assertEquals(alias, found[0].alias)
        assertEquals(category, found[0].category)
        assertTrue(publicKey.encoded.contentEquals(found[0].publicKey.array()))
        assertNotNull(found[0].schemeCodeName)
        assertNotNull(found[0].masterKeyAlias)
        assertNull(found[0].externalId)
        assertNull(found[0].hsmAlias)
    }

    private fun `Should be able to sign`(
        tenantId: String,
        publicKey: PublicKey
    ) {
        val data = randomDataByteArray()
        val signature = opsClient.sign(
            tenantId = tenantId,
            publicKey = publicKey,
            data = data
        )
        assertEquals(publicKey, signature.by)
        assertTrue(signature.bytes.isNotEmpty())
        verifier.verify(
            publicKey = publicKey,
            signatureData = signature.bytes,
            clearData = data
        )
    }

    private fun `Should be able to sign using custom signature spec`(
        tenantId: String,
        publicKey: PublicKey
    ) {
        val data = randomDataByteArray()
        val signatureSpec = when (publicKey.algorithm) {
            "EC" -> SignatureSpec("SHA512withECDSA")
            "RSA" -> SignatureSpec(
                "RSASSA-PSS",
                params = PSSParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    32,
                    1
                )
            )
            else -> throw IllegalArgumentException("Test supports only RSA or ECDSA")
        }
        val signature = opsClient.sign(
            tenantId = tenantId,
            publicKey = publicKey,
            signatureSpec = signatureSpec,
            data = data
        )
        assertEquals(publicKey, signature.by)
        assertTrue(signature.bytes.isNotEmpty())
        verifier.verify(
            publicKey = publicKey,
            signatureSpec = signatureSpec,
            signatureData = signature.bytes,
            clearData = data
        )
    }

    private fun `Should be able to sign by flow ops`(
        tenantId: String,
        publicKey: PublicKey
    ) {
        val data = randomDataByteArray()
        val key = UUID.randomUUID().toString()
        val event = transformer.createSign(
            tenantId = tenantId,
            publicKey = publicKey,
            data = data
        )
        publisher.publish(
            listOf(
                Record(
                    topic = FLOW_OPS_MESSAGE_TOPIC,
                    key = key,
                    value = event
                )
            )
        ).forEach { it.get() }
        val response = flowOpsResponses.waitForResponse(key)
        val signature = transformer.transform(response) as DigitalSignature.WithKey
        assertEquals(publicKey, signature.by)
        assertTrue(signature.bytes.isNotEmpty())
        verifier.verify(
            publicKey = publicKey,
            signatureData = signature.bytes,
            clearData = data
        )
    }
}
