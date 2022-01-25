package net.corda.processors.crypto.tests

import net.corda.crypto.CryptoConsts
import net.corda.crypto.CryptoOpsClient
import net.corda.crypto.service.CryptoOpsService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.crypto.SignatureSpec
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
import java.util.UUID
import kotlin.reflect.KFunction

@ExtendWith(ServiceExtension::class)
class CryptoOpsTests {
    companion object {
        private val logger = contextLogger()

        private val CLIENT_ID = makeClientId<CryptoOpsTests>()

        private const val CRYPTO_CONFIGURATION: String = "{}"

        private const val BOOT_CONFIGURATION = """
        instanceId=1
    """
    }

    @InjectService(timeout = 5000L)
    lateinit var coordinatorFactory: LifecycleCoordinatorFactory

    @InjectService(timeout = 5000L)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 5000L)
    lateinit var processor: CryptoProcessor

    @InjectService(timeout = 5000L)
    lateinit var client: CryptoOpsClient

    @InjectService(timeout = 5000L)
    lateinit var cipherSuiteFactory: CipherSuiteFactory

    private lateinit var testDependencies: TestLifecycleDependenciesTrackingCoordinator

    private lateinit var tenantId: String

    private fun <R> run(testCase: KFunction<R>): R = runTestCase(logger, testCase)

    private fun <R> run(testCaseArg: Any, testCase: KFunction<R>): R = runTestCase(logger, testCaseArg, testCase)

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()

        publisherFactory.publishConfig(
            CLIENT_ID,
            CRYPTO_CONFIGURATION to ConfigKeys.CRYPTO_CONFIG
        )

        processor.start(makeBootstrapConfig(BOOT_CONFIGURATION))

        testDependencies = TestLifecycleDependenciesTrackingCoordinator(
            logger,
            coordinatorFactory,
            CryptoOpsClient::class.java,
            CryptoProcessor::class.java
        )

        client.startAndWait()
        testDependencies.waitUntilAllUp()
    }

    @AfterEach
    fun cleanup() {
        client.stopAndWait()
        testDependencies.close()
    }

    @Test
    @Timeout(60)
    fun `Should be able to use crypto operations`() {
        run(::`Should be able to get supported schemes for all categories`)
        val keyAlias = UUID.randomUUID().toString()
        val publicKey = run(keyAlias, ::`Should generate new key pair`)
        run(keyAlias to publicKey, ::`Should find existing public key by its alias`)
        run(::`Should not find unknown public key by its alias`)
        // add back when the HSM registration is done
        //val freshPublicKey1 = run(::`Should generate new fresh key pair without external id`)
        //val externalId = UUID.randomUUID()
        //val freshPublicKey2 = run(externalId, ::`Should generate new fresh key pair with external id`)
        run(keyAlias to publicKey, ::`Should be able to sign by referencing key alias`)
        run(
            keyAlias to publicKey,
            ::`Should be able to sign using custom signature spec by referencing key alias`
        )
        run(publicKey, ::`Should be able to sign by referencing public key`)
        run(publicKey, ::`Should be able to sign using custom signature spec by referencing public key`)
        //run(freshPublicKey1, ::`Should be able to sign by referencing public key`)
        //run(freshPublicKey1, ::`Should be able to sign using custom signature spec by referencing public key`)
        //run(freshPublicKey2, ::`Should be able to sign by referencing public key`)
        //run(freshPublicKey2, ::`Should be able to sign using custom signature spec by referencing public key`)
    }

    private fun `Should be able to get supported schemes for all categories`() {
        val categories = listOf(
            CryptoConsts.CryptoCategories.LEDGER,
            CryptoConsts.CryptoCategories.FRESH_KEYS,
            CryptoConsts.CryptoCategories.AUTHENTICATION,
            CryptoConsts.CryptoCategories.TLS
        )
        categories.forEach { category ->
            logger.info("category=$category")
            val supportedSchemes = client.getSupportedSchemes(tenantId, category)
            assertTrue(supportedSchemes.isNotEmpty())
        }
    }

    private fun `Should generate new key pair`(keyAlias: String): PublicKey {
        return client.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.CryptoCategories.LEDGER,
            alias = keyAlias
        )
    }

    private fun `Should find existing public key by its alias`(expected: Pair<String, PublicKey>) {
        val publicKey = client.findPublicKey(
            tenantId = tenantId,
            alias = expected.first
        )
        assertNotNull(publicKey)
        assertEquals(expected.second, publicKey)
    }

    private fun `Should not find unknown public key by its alias`() {
        val publicKey = client.findPublicKey(
            tenantId = tenantId,
            alias = UUID.randomUUID().toString()
        )
        assertNull(publicKey)
    }

    private fun `Should generate new fresh key pair without external id`(): PublicKey {
        return client.freshKey(tenantId = tenantId)
    }

    private fun `Should generate new fresh key pair with external id`(externalId: UUID): PublicKey {
        return client.freshKey(tenantId = tenantId, externalId = externalId)
    }

    private fun `Should be able to sign by referencing key alias`(params: Pair<String, PublicKey>) {
        val data = randomDataByteArray()
        val signature = client.sign(
            tenantId = tenantId,
            alias = params.first,
            data = data
        )
        assertTrue(signature.isNotEmpty())
        cipherSuiteFactory.getSignatureVerificationService().verify(
            publicKey = params.second,
            signatureData = signature,
            clearData = data
        )
    }

    private fun `Should be able to sign using custom signature spec by referencing key alias`(
        params: Pair<String, PublicKey>
    ) {
        val data = randomDataByteArray()
        val signatureSpec = SignatureSpec("SHA512withECDSA")
        val signature = client.sign(
            tenantId = tenantId,
            alias = params.first,
            signatureSpec = signatureSpec,
            data = data
        )
        assertTrue(signature.isNotEmpty())
        cipherSuiteFactory.getSignatureVerificationService().verify(
            publicKey = params.second,
            signatureSpec = signatureSpec,
            signatureData = signature,
            clearData = data
        )
    }

    private fun `Should be able to sign by referencing public key`(publicKey: PublicKey) {
        val data = randomDataByteArray()
        val signature = client.sign(
            tenantId = tenantId,
            publicKey = publicKey,
            data = data
        )
        assertEquals(publicKey, signature.by)
        assertTrue(signature.bytes.isNotEmpty())
        cipherSuiteFactory.getSignatureVerificationService().verify(
            publicKey = publicKey,
            signatureData = signature.bytes,
            clearData = data
        )
    }

    private fun `Should be able to sign using custom signature spec by referencing public key`(publicKey: PublicKey) {
        val data = randomDataByteArray()
        val signatureSpec = SignatureSpec("SHA512withECDSA")
        val signature = client.sign(
            tenantId = tenantId,
            publicKey = publicKey,
            signatureSpec = signatureSpec,
            data = data
        )
        assertEquals(publicKey, signature.by)
        assertTrue(signature.bytes.isNotEmpty())
        cipherSuiteFactory.getSignatureVerificationService().verify(
            publicKey = publicKey,
            signatureSpec = signatureSpec,
            signatureData = signature.bytes,
            clearData = data
        )
    }
}