package net.corda.processors.crypto.tests

import com.typesafe.config.ConfigFactory
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.data.config.Configuration
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Crypto.Companion.FLOW_OPS_MESSAGE_TOPIC
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.SignatureVerificationService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KFunction

@ExtendWith(ServiceExtension::class)
class IntegrationCryptoOpsTests {
    companion object {
        private val logger = contextLogger()

        private val CLIENT_ID = makeClientId<IntegrationCryptoOpsTests>()

        private const val RESPONSE_TOPIC = "test.response"

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

        private const val BOOT_CONFIGURATION = """
        instance.id=1
    """
    }

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

    private lateinit var publisher: Publisher

    private lateinit var flowOpsResponses: FlowOpsResponses

    private lateinit var transformer: CryptoFlowOpsTransformer

    private lateinit var testDependencies: TestLifecycleDependenciesTrackingCoordinator

    private lateinit var tenantId: String

    private fun <R> run(testCase: KFunction<R>): R = runTestCase(logger, testCase)

    private fun <R> run(testCaseArg: Any, testCase: KFunction<R>): R = runTestCase(logger, testCaseArg, testCase)

    class FlowOpsResponses(
        subscriptionFactory: SubscriptionFactory
    ) : DurableProcessor<String, FlowOpsResponse>, AutoCloseable {

        private val subscription: Subscription<String, FlowOpsResponse> =
            subscriptionFactory.createDurableSubscription(
                subscriptionConfig = SubscriptionConfig(
                    groupName = "TEST",
                    eventTopic = RESPONSE_TOPIC
                ),
                processor = this,
                messagingConfig = SmartConfigFactory.create(
                    ConfigFactory.empty()).create(ConfigFactory.parseString(MESSAGING_CONFIGURATION_VALUE)
                    .withFallback(ConfigFactory.parseString(BOOT_CONFIGURATION))
                ),
                partitionAssignmentListener = null
            ).also { it.start() }

        private val receivedEvents = ConcurrentHashMap<String, FlowOpsResponse?>()

        override val keyClass: Class<String> = String::class.java

        override val valueClass: Class<FlowOpsResponse> = FlowOpsResponse::class.java

        override fun onNext(events: List<Record<String, FlowOpsResponse>>): List<Record<*, *>> {
            events.forEach {
                receivedEvents[it.key] = it.value
            }
            return emptyList()
        }

        fun waitForResponse(key: String): FlowOpsResponse =
            eventually {
                val event = receivedEvents[key]
                assertNotNull(event)
                event!!
            }

        override fun close() {
            subscription.close()
        }
    }

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()

        logger.info("Starting ${opsClient::class.java.simpleName}")
        opsClient.startAndWait()

        logger.info("Starting ${processor::class.java.simpleName}")
        processor.startAndWait(makeBootstrapConfig(BOOT_CONFIGURATION))

        testDependencies = TestLifecycleDependenciesTrackingCoordinator(
            LifecycleCoordinatorName.forComponent<IntegrationCryptoOpsTests>(),
            coordinatorFactory,
            CryptoOpsClient::class.java,
            CryptoProcessor::class.java
        ).also { it.startAndWait() }

        logger.info("Publishing configs for $CRYPTO_CONFIG and $MESSAGING_CONFIG")
        publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), SmartConfigImpl.empty())
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

        flowOpsResponses = FlowOpsResponses(subscriptionFactory)

        transformer = CryptoFlowOpsTransformer(
            requestingComponent = "test",
            responseTopic = RESPONSE_TOPIC,
            keyEncodingService = keyEncodingService
        )

        testDependencies.waitUntilAllUp(Duration.ofSeconds(10))
    }

    @AfterEach
    fun cleanup() {
        flowOpsResponses.close()
        opsClient.stopAndWait()
        testDependencies.stopAndWait()
    }

    @Test
    @Timeout(200)
    fun `Should be able to use crypto operations`() {
        run(::`Should be able to get supported schemes for all categories`)
        val ledgerKeyAlias = UUID.randomUUID().toString()
        val tlsKeyAlias = UUID.randomUUID().toString()
        val ledgerPublicKey = run(ledgerKeyAlias, ::`Should generate new key pair for LEDGER`)
        run(ledgerKeyAlias to ledgerPublicKey, ::`Should find existing public key by its alias`)
        val tlsPublicKey = run(tlsKeyAlias, ::`Should generate new key pair for TLS`)
        run(ledgerKeyAlias to ledgerPublicKey, ::`Should find existing public key by its alias`)
        run(tlsKeyAlias to tlsPublicKey, ::`Should find existing public key by its alias`)
        run(::`Should not find unknown public key by its alias`)
        run(ledgerKeyAlias to ledgerPublicKey, ::`Should be able to sign by referencing key alias`)
        run(
            ledgerKeyAlias to ledgerPublicKey,
            ::`Should be able to sign using custom signature spec by referencing key alias`
        )
        run(tlsKeyAlias to tlsPublicKey, ::`Should be able to sign by referencing key alias`)
        run(
            tlsKeyAlias to tlsPublicKey,
            ::`Should be able to sign using custom signature spec by referencing key alias`
        )
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

    private fun `Should find existing public key by its alias`(expected: Pair<String, PublicKey>) {
        val publicKey = opsClient.findPublicKey(
            tenantId = tenantId,
            alias = expected.first
        )
        assertNotNull(publicKey)
        assertEquals(expected.second, publicKey)
    }

    private fun `Should not find unknown public key by its alias`() {
        val publicKey = opsClient.findPublicKey(
            tenantId = tenantId,
            alias = UUID.randomUUID().toString()
        )
        assertNull(publicKey)
    }

    private fun `Should generate new fresh key pair without external id`(): PublicKey {
        return opsClient.freshKey(tenantId = tenantId)
    }

    private fun `Should generate new fresh key pair with external id`(externalId: UUID): PublicKey {
        return opsClient.freshKey(tenantId = tenantId, externalId = externalId)
    }

    private fun `Should be able to generate fresh key without external id by flow ops`(): PublicKey {
        val key = UUID.randomUUID().toString()
        val event = transformer.createFreshKey(
            tenantId = tenantId
        )
        publisher.publish(listOf(
            Record(
                topic = FLOW_OPS_MESSAGE_TOPIC,
                key = key,
                value = event
            )
        )).forEach { it.get() }
        val response = flowOpsResponses.waitForResponse(key)
        return transformer.transform(response) as PublicKey
    }

    private fun `Should be able to generate fresh key with external id by flow ops`(externalId: UUID): PublicKey {
        val key = UUID.randomUUID().toString()
        val event = transformer.createFreshKey(
            tenantId = tenantId,
            externalId = externalId
        )
        publisher.publish(listOf(
            Record(
                topic = FLOW_OPS_MESSAGE_TOPIC,
                key = key,
                value = event
            )
        )).forEach { it.get() }
        val response = flowOpsResponses.waitForResponse(key)
        return transformer.transform(response) as PublicKey
    }

    private fun `Should be able to sign by referencing key alias`(params: Pair<String, PublicKey>) {
        val data = randomDataByteArray()
        val signature = opsClient.sign(
            tenantId = tenantId,
            alias = params.first,
            data = data
        )
        assertTrue(signature.isNotEmpty())
        verifier.verify(
            publicKey = params.second,
            signatureData = signature,
            clearData = data
        )
    }

    private fun `Should be able to sign using custom signature spec by referencing key alias`(
        params: Pair<String, PublicKey>
    ) {
        val data = randomDataByteArray()
        val signatureSpec = when (params.second.algorithm) {
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
            alias = params.first,
            signatureSpec = signatureSpec,
            data = data
        )
        assertTrue(signature.isNotEmpty())
        verifier.verify(
            publicKey = params.second,
            signatureSpec = signatureSpec,
            signatureData = signature,
            clearData = data
        )
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
            "RSA" -> SignatureSpec("SHA512withECDSA")
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
        publisher.publish(listOf(
            Record(
                topic = FLOW_OPS_MESSAGE_TOPIC,
                key = key,
                value = event
            )
        )).forEach { it.get() }
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
