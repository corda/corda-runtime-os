package net.corda.processors.crypto.tests

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.data.config.Configuration
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
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
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.use
import net.corda.processors.crypto.CryptoProcessor
import net.corda.processors.crypto.tests.infra.BOOT_CONFIGURATION
import net.corda.processors.crypto.tests.infra.CRYPTO_CONFIGURATION_VALUE
import net.corda.processors.crypto.tests.infra.FlowOpsResponses
import net.corda.processors.crypto.tests.infra.MESSAGING_CONFIGURATION_VALUE
import net.corda.processors.crypto.tests.infra.RESPONSE_TOPIC
import net.corda.processors.crypto.tests.infra.TestLifecycleDependenciesTrackingCoordinator
import net.corda.processors.crypto.tests.infra.makeBootstrapConfig
import net.corda.processors.crypto.tests.infra.makeClientId
import net.corda.processors.crypto.tests.infra.randomDataByteArray
import net.corda.processors.crypto.tests.infra.runTestCase
import net.corda.processors.crypto.tests.infra.startAndWait
import net.corda.processors.crypto.tests.infra.stopAndWait
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Crypto.Companion.FLOW_OPS_MESSAGE_TOPIC
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
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
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import kotlin.reflect.KFunction

@ExtendWith(ServiceExtension::class)
class CryptoProcessorTests {
    companion object {
        private val logger = contextLogger()

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
        lateinit var dbAdmin: DbAdmin

        @InjectService(timeout = 5000)
        lateinit var entitiesRegistry: JpaEntitiesRegistry

        @InjectService(timeout = 5000)
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService(timeout = 5000)
        lateinit var lbm: LiquibaseSchemaMigrator

        private lateinit var databaseInstaller: DatabaseInstaller

        private lateinit var configEmf: EntityManagerFactory

        private lateinit var cryptoEmf: EntityManagerFactory

        private lateinit var tenantEmf: EntityManagerFactory

        private lateinit var publisher: Publisher

        private lateinit var flowOpsResponses: FlowOpsResponses

        private lateinit var transformer: CryptoFlowOpsTransformer

        private lateinit var testDependencies: TestLifecycleDependenciesTrackingCoordinator

        private val tenantId: String = UUID.randomUUID().toString()

        private val configFactory = SmartConfigFactory.create(
            ConfigFactory.parseString(
                """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=key
            ${SmartConfigFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            )
        )

        private val config: SmartConfig = configFactory.create(DbUtils.createConfig("configuration_db"))

        private val cryptoDbConfig: EntityManagerConfiguration =
            DbUtils.getEntityManagerConfiguration(CordaDb.Crypto.persistenceUnitName)

        private val tenantDbConfig: EntityManagerConfiguration =
            DbUtils.getEntityManagerConfiguration("vnode_crypto_$tenantId")

        private val configDbConfig: EntityManagerConfiguration =
            DbUtils.getEntityManagerConfiguration("configuration_db")

        @JvmStatic
        @BeforeAll
        fun setup() {
            databaseInstaller = DatabaseInstaller(entityManagerFactoryFactory, lbm, entitiesRegistry)
            flowOpsResponses = FlowOpsResponses(subscriptionFactory)
            transformer = CryptoFlowOpsTransformer(
                requestingComponent = "test",
                responseTopic = RESPONSE_TOPIC,
                keyEncodingService = keyEncodingService
            )
            logger.info("Publishing configs for $CRYPTO_CONFIG and $MESSAGING_CONFIG")
            publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))
            setupMessagingAndCryptoConfigs()
            setupDatabases()
            startDependencies()
            dbConnectionManager.bootstrap(config)
            testDependencies.waitUntilAllUp(Duration.ofSeconds(10))
            //dbAdmin.createDbAndUser(DbSchema.CRYPTO, "dml_user", "pwd_123", DbPrivilege.DML)
            // temporary hack as the dbAdmin doesn't support HSQL
            addDbConnectionConfigs(CordaDb.Crypto.persistenceUnitName, "vnode_crypto_$tenantId")
        }

        private fun addDbConnectionConfigs(vararg names: String) {
            configEmf.use { em ->
                em.transaction.begin()
                names.forEach { name ->
                    val record = DbConnectionConfig(
                        id = UUID.randomUUID(),
                        name = name,
                        privilege = DbPrivilege.DML,
                        updateTimestamp = Instant.now(),
                        updateActor = "sa",
                        config = configFactory.create(DbUtils.createConfig(name)).root().render(
                            ConfigRenderOptions.defaults()
                                .setComments(false)
                                .setFormatted(false)
                                .setJson(true)
                                .setOriginComments(false)
                        ),
                        description = null
                    )
                    em.persist(record)
                    logger.info("persisting config record: ${record.config}")
                }
                em.transaction.commit()
            }
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            if (::cryptoEmf.isInitialized) {
                cryptoEmf.close()
            }
            if(::tenantEmf.isInitialized) {
                tenantDbConfig.close()
            }
            if (::configEmf.isInitialized) {
                configEmf.close()
            }
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
            configEmf = databaseInstaller.setupDatabase(
                configDbConfig,
                "config",
                CordaDb.CordaCluster.persistenceUnitName,
                ConfigurationEntities.classes
            )
            cryptoEmf = databaseInstaller.setupDatabase(
                cryptoDbConfig,
                "crypto",
                CordaDb.Crypto.persistenceUnitName,
                CryptoEntities.classes
            )
            tenantEmf = databaseInstaller.setupDatabase(
                tenantDbConfig,
                "crypto",
                "vnode_crypto_$tenantId",
                CryptoEntities.classes
            )
        }

        private fun <R> run(testCase: KFunction<R>): R = runTestCase(logger, testCase)

        private fun <R> run(testCaseArg: Any, testCase: KFunction<R>): R = runTestCase(logger, testCaseArg, testCase)
    }

    @Test
    fun `Should be able to use crypto operations`() {
        run(::`Should be able to get supported schemes for all categories`)
        val ledgerKeyAlias = UUID.randomUUID().toString()
        val tlsKeyAlias = UUID.randomUUID().toString()
        val ledgerPublicKey = run(ledgerKeyAlias, ::`Should generate new key pair for LEDGER`)
        run(ledgerKeyAlias to ledgerPublicKey, ::`Should find existing public key by its id`)
        val tlsPublicKey = run(tlsKeyAlias, ::`Should generate new key pair for TLS`)
        run(ledgerKeyAlias to ledgerPublicKey, ::`Should find existing public key by its id`)
        run(tlsKeyAlias to tlsPublicKey, ::`Should find existing public key by its id`)
        run(::`Should not find unknown public key by its id`)
        run(ledgerPublicKey, ::`Should be able to sign by referencing public key`)
        run(tlsPublicKey, ::`Should be able to sign by referencing public key`)
        run(ledgerPublicKey, ::`Should be able to sign using custom signature spec by referencing public key`)
        run(tlsPublicKey, ::`Should be able to sign using custom signature spec by referencing public key`)
        run(ledgerPublicKey, ::`Should be able to sign by flow ops`)

        // add back when the HSM registration is done, due that the wrapping key is created at the point
        // when the HSM is allocated to a tenant

        //val freshPublicKey1 = run(::`Should generate new fresh key pair without external id`)
        //val externalId2 = UUID.randomUUID()
        //val freshPublicKey2 = run(externalId2, ::`Should generate new fresh key pair with external id`)
        //run(freshPublicKey1, ::`Should be able to sign by referencing public key`)
        //run(freshPublicKey1, ::`Should be able to sign using custom signature spec by referencing public key`)
        //run(freshPublicKey2, ::`Should be able to sign by referencing public key`)
        //run(freshPublicKey2, ::`Should be able to sign using custom signature spec by referencing public key`)
        //val freshPublicKey3 = run(::`Should be able to generate fresh key without external id by flow ops`)
        //val externalId4 = UUID.randomUUID()
        //val freshPublicKey4 = run(externalId4, ::`Should generate new fresh key pair with external id`)
        //run(freshPublicKey3, ::`Should be able to sign by flow ops`)
        //run(freshPublicKey4, ::`Should be able to sign by flow ops`)
    }

    private fun `Should be able to get supported schemes for all categories`() {
        val categories = listOf(
            CryptoConsts.HsmCategories.LEDGER,
            CryptoConsts.HsmCategories.FRESH_KEYS,
            CryptoConsts.HsmCategories.SESSION,
            CryptoConsts.HsmCategories.TLS
        )
        categories.forEach { category ->
            logger.info("category=$category")
            val supportedSchemes = opsClient.getSupportedSchemes(tenantId, category)
            assertTrue(supportedSchemes.isNotEmpty())
        }
    }

    private fun `Should generate new key pair for LEDGER`(keyAlias: String): PublicKey {
        return opsClient.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.HsmCategories.LEDGER,
            alias = keyAlias
        )
    }

    private fun `Should generate new key pair for TLS`(keyAlias: String): PublicKey {
        return opsClient.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.HsmCategories.LEDGER,
            alias = keyAlias
        )
    }

    private fun `Should find existing public key by its id`(expected: Pair<String, PublicKey>) {
        val publicKey = opsClient.lookup(
            tenantId = tenantId,
            ids = listOf(publicKeyIdOf(expected.second))
        )
        assertNotNull(publicKey)
        assertEquals(expected.second, publicKey)
    }

    private fun `Should not find unknown public key by its id`() {
        val publicKey = opsClient.lookup(
            tenantId = tenantId,
            ids = listOf(publicKeyIdOf(UUID.randomUUID().toString().toByteArray()))
        )
        assertNull(publicKey)
    }

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
}
