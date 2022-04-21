package net.corda.processors.crypto.tests

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.data.config.Configuration
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.db.testkit.DatabaseInstaller
import net.corda.libs.configuration.datamodel.ConfigurationEntities
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
import net.corda.processors.crypto.CryptoProcessor
import net.corda.processors.crypto.tests.infra.BOOT_CONFIGURATION
import net.corda.processors.crypto.tests.infra.CRYPTO_CONFIGURATION_VALUE
import net.corda.processors.crypto.tests.infra.FlowOpsResponses
import net.corda.processors.crypto.tests.infra.MESSAGING_CONFIGURATION_VALUE
import net.corda.processors.crypto.tests.infra.RESPONSE_TOPIC
import net.corda.processors.crypto.tests.infra.TestDbInfo
import net.corda.processors.crypto.tests.infra.TestLifecycleDependenciesTrackingCoordinator
import net.corda.processors.crypto.tests.infra.makeBootstrapConfig
import net.corda.processors.crypto.tests.infra.makeClientId
import net.corda.processors.crypto.tests.infra.randomDataByteArray
import net.corda.processors.crypto.tests.infra.startAndWait
import net.corda.processors.crypto.tests.infra.stopAndWait
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Crypto.Companion.FLOW_OPS_MESSAGE_TOPIC
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.SignatureVerificationService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.time.Duration
import java.util.UUID

@ExtendWith(ServiceExtension::class)
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
        lateinit var processor: CryptoProcessor

        @InjectService(timeout = 5000L)
        lateinit var opsClient: CryptoOpsClient

        @InjectService(timeout = 5000L)
        lateinit var verifier: SignatureVerificationService

        @InjectService(timeout = 5000L)
        lateinit var keyEncodingService: KeyEncodingService

        @InjectService(timeout = 5000)
        lateinit var dbConnectionManager: DbConnectionManager

        @InjectService(timeout = 5000)
        lateinit var entitiesRegistry: JpaEntitiesRegistry

        @InjectService(timeout = 5000)
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService(timeout = 5000)
        lateinit var lbm: LiquibaseSchemaMigrator

        private lateinit var publisher: Publisher

        private lateinit var flowOpsResponses: FlowOpsResponses

        private lateinit var transformer: CryptoFlowOpsTransformer

        private lateinit var testDependencies: TestLifecycleDependenciesTrackingCoordinator

        private val tenantId: String = UUID.randomUUID().toString()

        private val clusterDb = TestDbInfo(CordaDb.CordaCluster.persistenceUnitName)

        private val cryptoDb = TestDbInfo(CordaDb.Crypto.persistenceUnitName)

        private val tenantDb = TestDbInfo("vnode_crypto_$tenantId")

        @JvmStatic
        @BeforeAll
        fun setup() {
            setupPrerequisites()
            setupMessagingAndCryptoConfigs()
            setupDatabases()
            startDependencies()
            dbConnectionManager.bootstrap(clusterDb.config)
            testDependencies.waitUntilAllUp(Duration.ofSeconds(10))
            // temporary hack as the DbAdmin doesn't support HSQL
            addDbConnectionConfigs(cryptoDb, tenantDb)
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            if (::testDependencies.isInitialized) {
                testDependencies.stopAndWait()
            }
            if (::flowOpsResponses.isInitialized) {
                flowOpsResponses.close()
            }
            if (::opsClient.isInitialized) {
                opsClient.stopAndWait()
            }
        }

        private fun setupPrerequisites() {
            flowOpsResponses = FlowOpsResponses(subscriptionFactory)
            transformer = CryptoFlowOpsTransformer(
                requestingComponent = "test",
                responseTopic = RESPONSE_TOPIC,
                keyEncodingService = keyEncodingService
            )
            publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))
        }

        private fun addDbConnectionConfigs(vararg dbs: TestDbInfo) {
            dbs.forEach { db ->
                dbConnectionManager.putConnection(
                    name = db.name,
                    privilege = DbPrivilege.DML,
                    config = db.config,
                    description = null,
                    updateActor = "sa"
                )
            }
        }

        private fun startDependencies() {
            opsClient.startAndWait()
            processor.startAndWait(makeBootstrapConfig(BOOT_CONFIGURATION))
            testDependencies = TestLifecycleDependenciesTrackingCoordinator(
                LifecycleCoordinatorName.forComponent<CryptoProcessorTests>(),
                coordinatorFactory,
                lifecycleRegistry,
                setOf(
                    LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                    LifecycleCoordinatorName.forComponent<CryptoProcessor>()
                )
            ).also { it.startAndWait() }
        }

        private fun setupMessagingAndCryptoConfigs() {
            with(publisher) {
                publish(
                    listOf(
                        Record(
                            CONFIG_TOPIC,
                            MESSAGING_CONFIG,
                            Configuration(MESSAGING_CONFIGURATION_VALUE, "1")
                        ),
                        Record(
                            CONFIG_TOPIC,
                            CRYPTO_CONFIG,
                            Configuration(CRYPTO_CONFIGURATION_VALUE, "1")
                        )
                    )
                )
            }
        }

        private fun setupDatabases() {
            val databaseInstaller = DatabaseInstaller(entityManagerFactoryFactory, lbm, entitiesRegistry)
            databaseInstaller.setupDatabase(
                clusterDb.emConfig,
                "config",
                clusterDb.name,
                ConfigurationEntities.classes
            ).close()
            databaseInstaller.setupDatabase(
                cryptoDb.emConfig,
                "crypto",
                cryptoDb.name,
                CryptoEntities.classes
            ).close()
            databaseInstaller.setupDatabase(
                tenantDb.emConfig,
                "crypto",
                tenantDb.name,
                CryptoEntities.classes
            ).close()
        }

        @JvmStatic
        fun testCategories(): Array<String> = arrayOf(
            CryptoConsts.HsmCategories.LEDGER,
            CryptoConsts.HsmCategories.TLS,
            CryptoConsts.HsmCategories.SESSION
        )
    }

    @ParameterizedTest
    @MethodSource("testCategories")
    fun `Should be able to get supported schemes`(category: String) {
        val supportedSchemes = opsClient.getSupportedSchemes(tenantId, category)
        assertTrue(supportedSchemes.isNotEmpty())
    }

    @Test
    fun `Should not find unknown public key by its id`() {
        val publicKey = opsClient.lookup(
            tenantId = tenantId,
            ids = listOf(publicKeyIdOf(UUID.randomUUID().toString().toByteArray()))
        )
        assertNull(publicKey)
    }

    @Test
    fun `Should return empty collection when lookp filter does not match`() {
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
    fun `Should generate a new key pair using alias find it and use it for signing`(category: String) {
        val alias = UUID.randomUUID().toString()

        val original = opsClient.generateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = alias
        )

        `Should find existing public key by its id`(original)

        `Should find existing public key by its alias`(alias, original, category)

        `Should be able to sign by referencing public key`(original)

        `Should be able to sign using custom signature spec by referencing public key`(original)

        `Should be able to sign by flow ops`(original)
    }

    private fun `Should find existing public key by its id`(publicKey: PublicKey) {
        val found = opsClient.lookup(
            tenantId = tenantId,
            ids = listOf(publicKeyIdOf(publicKey))
        )
        assertNotNull(found)
        assertEquals(publicKey, found)
    }

    private fun `Should find existing public key by its alias`(
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
        assertEquals(publicKeyIdOf(publicKey), found[0].id)
        assertEquals(tenantId, found[0].tenantId)
        assertEquals(alias, found[0].alias)
        assertEquals(category, found[0].category)
        assertTrue(publicKey.encoded.contentEquals(found[0].publicKey.array()))
        assertNotNull(found[0].schemeCodeName)
        assertNotNull(found[0].masterKeyAlias)
        assertNull(found[0].externalId)
        assertNull(found[0].hsmAlias)
    }

    private fun `Should be able to sign by referencing public key`(publicKey: PublicKey) {
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

    private fun `Should be able to sign using custom signature spec by referencing public key`(publicKey: PublicKey) {
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

    private fun `Should be able to sign by flow ops`(publicKey: PublicKey) {
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

    // add back when the HSM registration is done, due that the wrapping key is created at the point
    // when the HSM is allocated to a tenant

    private fun `Should generate new fresh key pair without external id`(): PublicKey =
        opsClient.freshKey(
            tenantId = tenantId,
            context = CryptoOpsClient.EMPTY_CONTEXT
        )

    private fun `Should generate new fresh key pair with external id`(externalId: String): PublicKey =
        opsClient.freshKey(
            tenantId = tenantId,
            externalId = externalId,
            context = CryptoOpsClient.EMPTY_CONTEXT
        )

    private fun `Should be able to generate fresh key without external id by flow ops`(): PublicKey {
        val key = UUID.randomUUID().toString()
        val event = transformer.createFreshKey(
            tenantId = tenantId
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
        return transformer.transform(response) as PublicKey
    }

    private fun `Should be able to generate fresh key with external id by flow ops`(externalId: UUID): PublicKey {
        val key = UUID.randomUUID().toString()
        val event = transformer.createFreshKey(
            tenantId = tenantId,
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
        return transformer.transform(response) as PublicKey
    }
}
