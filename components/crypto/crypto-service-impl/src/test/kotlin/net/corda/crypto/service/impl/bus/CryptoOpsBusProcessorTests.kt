package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.config.impl.createTestCryptoConfig
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.Categories.CI
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.impl.toWire
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.crypto.service.impl.infra.TestServicesFactory.Companion.CTX_TRACKING
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoDerivedSharedSecret
import net.corda.data.crypto.wire.CryptoKeySchemes
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.hsm.registration.commands.AssignHSMCommand
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.commands.DeriveSharedSecretCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateWrappingKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.queries.ByIdsRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.crypto.wire.ops.rpc.queries.KeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.SupportedSchemesRpcQuery
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.X25519_CODE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CryptoOpsBusProcessorTests {
    companion object {
        private val configEvent = ConfigChangedEvent(
            setOf(ConfigKeys.CRYPTO_CONFIG),
            mapOf(ConfigKeys.CRYPTO_CONFIG to createTestCryptoConfig(KeyCredentials("pass", "salt")))
        )
        private val basicContext = KeyValuePairList(
            listOf(
                KeyValuePair(CTX_TRACKING, UUID.randomUUID().toString()),
                KeyValuePair("reason", "Hello World!")
            )
        )
        private val emptyContext = KeyValuePairList(emptyList())
    }

    private lateinit var factory: TestServicesFactory
    private lateinit var tenantId: String
    private lateinit var signingFactory: SigningServiceFactory
    private lateinit var processor: CryptoOpsBusProcessor


    @BeforeAll
    fun setup() {
        // none of these services store critical state, so we are safe to share them between
        // test runs. This test class takes 1.7s for me if this is @BeforeAll, compared with 15s
        // if this is @BeforeEach.
        tenantId = UUID.randomUUID().toString()
        factory = TestServicesFactory()
        signingFactory = mock {
            on { getInstance() } doReturn factory.signingService
        }
        processor = CryptoOpsBusProcessor(signingFactory, configEvent)
        CryptoConsts.Categories.all.forEach {
            factory.hsmService.assignSoftHSM(tenantId, it)
        }
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun createRequestContext(): CryptoRequestContext = CryptoRequestContext(
        "test-component",
        Instant.now(),
        UUID.randomUUID().toString(),
        tenantId,
        KeyValuePairList(
            listOf(
                KeyValuePair("key1", "value1"),
                KeyValuePair("key2", "value2")
            )
        )
    )

    private fun assertResponseContext(expected: CryptoRequestContext, actual: CryptoResponseContext) {
        val now = Instant.now()
        assertEquals(expected.tenantId, actual.tenantId)
        assertEquals(expected.requestId, actual.requestId)
        assertEquals(expected.requestingComponent, actual.requestingComponent)
        assertEquals(expected.requestTimestamp, actual.requestTimestamp)
        assertThat(actual.responseTimestamp.epochSecond)
            .isGreaterThanOrEqualTo(expected.requestTimestamp.epochSecond)
            .isLessThanOrEqualTo(now.epochSecond)
        assertTrue(
            actual.other.items.size == expected.other.items.size &&
                    actual.other.items.containsAll(expected.other.items) &&
                    expected.other.items.containsAll(actual.other.items)
        )
    }


    private fun assertOperationContextMap(l: KeyValuePairList, category: String) {
        val operationContextMap = factory.recordedCryptoContexts[l.items[0].value]
        assertNotNull(operationContextMap)
        assertEquals(4, operationContextMap.size)
        assertEquals(l.items[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(l.items[1].value, operationContextMap["reason"])
        assertEquals(tenantId, operationContextMap[CRYPTO_TENANT_ID])
        assertEquals(category, operationContextMap[CRYPTO_CATEGORY])
    }

    private inline fun <reified T> process(request: Any): T {
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(RpcOpsRequest(context, request), future)
        val result = future.get()!!
        assertResponseContext(context, result.context)
        assertNotNull(result.response)
        return (result.response) as T
    }

    @Test
    fun `Should return empty list for unknown key id`() {
        val keyEnc = publicKeyIdFromBytes(UUID.randomUUID().toString().toByteArray())
        val response = process<CryptoSigningKeys>(ByIdsRpcQuery(listOf(keyEnc)))
        assertEquals(0, response.keys.size)
    }

    @Test
    fun `Should return empty list for look up when the filter does not match`() {
        val context = KeyValuePairList(listOf(KeyValuePair(ALIAS_FILTER, UUID.randomUUID().toString())))
        val keys = process<CryptoSigningKeys>(KeysRpcQuery(0, 10, CryptoKeyOrderBy.NONE, context))
        assertEquals(0, keys.keys.size)
    }

    @Test
    fun `Should generate key pair and be able to find and lookup and then sign using default and custom schemes`() {
        val data = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        // generate
        val command = GenerateKeyPairCommand(LEDGER, alias, null, ECDSA_SECP256R1_CODE_NAME, basicContext)
        val publicKey1 = process<CryptoPublicKey>(command)
        assertOperationContextMap(basicContext, LEDGER)
        val publicKey = factory.schemeMetadata.decodePublicKey(publicKey1.key.array())
        val info = factory.signingKeyStore.find(tenantId, publicKey)
        assertNotNull(info)
        assertEquals(alias, info.alias)
        // find
        val keys = process<CryptoSigningKeys>(ByIdsRpcQuery(listOf(publicKeyIdFromBytes(info.publicKey))))
        assertEquals(1, keys.keys.size)
        assertEquals(publicKey, factory.schemeMetadata.decodePublicKey(keys.keys[0].publicKey.array()))
        // lookup
        val aliasContext = KeyValuePairList(listOf(KeyValuePair(ALIAS_FILTER, alias)))
        val key3 = process<CryptoSigningKeys>(KeysRpcQuery(0, 20, CryptoKeyOrderBy.NONE, aliasContext))
        assertEquals(1, key3.keys.size)
        assertEquals(publicKey, factory.schemeMetadata.decodePublicKey(key3.keys[0].publicKey.array()))
        // signing
        testSigning(publicKey, data)
    }


    @Test
    fun `Second attempt to generate key with same alias should throw KeyAlreadyExistsException`() {
        val alias = newAlias()
        process<CryptoPublicKey>(GenerateKeyPairCommand(LEDGER, alias, null, ECDSA_SECP256R1_CODE_NAME, emptyContext))
        val command = GenerateKeyPairCommand(LEDGER, alias, null, ECDSA_SECP256R1_CODE_NAME, emptyContext)
        val exception = assertFailsWith<ExecutionException> { process<CryptoPublicKey>(command) }
        assertThat(exception.cause).isInstanceOf(KeyAlreadyExistsException::class.java)
    }

    @Test
    fun `Should generate key pair and be able to find and lookup and then sign with parameterised signature params`() {
        val spec4 = PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)
        val signatureSpec4 = ParameterizedSignatureSpec("RSASSA-PSS", spec4)
        val data = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        // generate
        val makeKeyCommand = GenerateKeyPairCommand(LEDGER, alias, null, RSA_CODE_NAME, basicContext)
        val originalKey = process<CryptoPublicKey>(makeKeyCommand)
        assertOperationContextMap(basicContext, LEDGER)
        val publicKey = factory.schemeMetadata.decodePublicKey(originalKey.key.array())
        val info = factory.signingKeyStore.find(tenantId, publicKey)
        assertNotNull(info)
        assertEquals(alias, info.alias)
        // find
        val findResult1 = process<CryptoSigningKeys>(ByIdsRpcQuery(listOf(publicKeyIdFromBytes(info.publicKey))))
        assertEquals(1, findResult1.keys.size)
        assertEquals(publicKey, factory.schemeMetadata.decodePublicKey(findResult1.keys[0].publicKey.array()))
        // lookup
        val queryFields = KeyValuePairList(listOf(KeyValuePair(ALIAS_FILTER, alias)))
        val findResult2 = process<CryptoSigningKeys>(KeysRpcQuery(0, 20, CryptoKeyOrderBy.NONE, queryFields))
        assertEquals(1, findResult2.keys.size)
        assertEquals(publicKey, factory.schemeMetadata.decodePublicKey(findResult2.keys[0].publicKey.array()))
        // sign
        val encKey4 = ByteBuffer.wrap(factory.schemeMetadata.encodeAsByteArray(publicKey))
        val params4 = factory.schemeMetadata.serialize(signatureSpec4.params)
        val parameterSpec = CryptoSignatureParameterSpec(params4.clazz, ByteBuffer.wrap(params4.bytes))
        val cryptoSignatureSpec4 = CryptoSignatureSpec(signatureSpec4.signatureName, null, parameterSpec)
        val signatureCommand = SignRpcCommand(encKey4, cryptoSignatureSpec4, ByteBuffer.wrap(data), emptyContext)
        val signature4 = process<CryptoSignatureWithKey>(signatureCommand)
        assertEquals(publicKey, factory.schemeMetadata.decodePublicKey(signature4.publicKey.array()))
        factory.verifier.verify(publicKey, signatureSpec4, signature4.bytes.array(), data)
    }

    @Test
    fun `Should generate fresh key pair without external id and be able to sign using default and custom schemes`() {
        val command = GenerateFreshKeyRpcCommand(CI, null, ECDSA_SECP256R1_CODE_NAME, basicContext)
        val key = process<CryptoPublicKey>(command)
        assertOperationContextMap(basicContext, CI)
        val publicKey = factory.schemeMetadata.decodePublicKey(key.key.array())
        val info = factory.signingKeyStore.find(tenantId, publicKey)
        assertNotNull(info)
        assertNull(info.alias)
        assertNull(info.externalId)
        testSigning(publicKey, UUID.randomUUID().toString().toByteArray())
    }

    @Test
    fun `Should handle generating fresh keys twice without external id`() {
        val key1 = process<CryptoPublicKey>(GenerateFreshKeyRpcCommand(CI, null, ECDSA_SECP256R1_CODE_NAME, emptyContext))
        val key2 = process<CryptoPublicKey>(GenerateFreshKeyRpcCommand(CI, null, ECDSA_SECP256R1_CODE_NAME, emptyContext))
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `Should handle generating fresh keys twice with external id`() {
        val externalId = UUID.randomUUID().toString()
        val key1 = process<CryptoPublicKey>(GenerateFreshKeyRpcCommand(CI, externalId, ECDSA_SECP256R1_CODE_NAME, emptyContext))
        val key2 = process<CryptoPublicKey>(GenerateFreshKeyRpcCommand(CI, externalId, ECDSA_SECP256R1_CODE_NAME, emptyContext))
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `Should generate fresh key pair with external id and be able to sign using default and custom schemes`() {
        val data = UUID.randomUUID().toString().toByteArray()
        // generate
        val externalId = UUID.randomUUID().toString()
        val key = process<CryptoPublicKey>(GenerateFreshKeyRpcCommand(CI, externalId, ECDSA_SECP256R1_CODE_NAME, basicContext))
        assertOperationContextMap(basicContext, CI)
        val publicKey = factory.schemeMetadata.decodePublicKey(key.key.array())
        val info = factory.signingKeyStore.find(tenantId, publicKey)
        assertNotNull(info)
        assertNull(info.alias)
        assertEquals(externalId, info.externalId)
        // signing
        testSigning(publicKey, data)
    }

    @Test
    fun `Should generate wrapping key`() {
        val context = KeyValuePairList(
            listOf(
                KeyValuePair(CTX_TRACKING, UUID.randomUUID().toString()),
                KeyValuePair("reason", "Hello World!"),
                KeyValuePair(CRYPTO_TENANT_ID, tenantId)
            )
        )
        val masterKeyAlias = UUID.randomUUID().toString()
        process<CryptoNoContentValue>(GenerateWrappingKeyRpcCommand(SOFT_HSM_ID, masterKeyAlias, true, context))
        val operationContextMap = factory.recordedCryptoContexts[context.items[0].value]
        assertNotNull(operationContextMap)
        assertEquals(3, operationContextMap.size)
        assertEquals(context.items[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(context.items[1].value, operationContextMap["reason"])
        assertEquals(tenantId, operationContextMap[CRYPTO_TENANT_ID])
        assertThat(factory.wrappingKeyStore.keys).containsKey(masterKeyAlias)
    }

    @Test
    fun `Should derive shared secret key`() {
        val context = KeyValuePairList(
            listOf(
                KeyValuePair(CTX_TRACKING, UUID.randomUUID().toString()),
                KeyValuePair("reason", "Hello World!"),
                KeyValuePair(CRYPTO_TENANT_ID, tenantId)
            )
        )
        val otherKeyPair = generateKeyPair(factory.schemeMetadata, X25519_CODE_NAME)
        val ks = factory.schemeMetadata.findKeyScheme(X25519_CODE_NAME)
        val publicKey = factory.signingService.generateKeyPair(tenantId, SESSION_INIT, "ecd-key", ks)
        val keyEnc = ByteBuffer.wrap(factory.schemeMetadata.encodeAsByteArray(publicKey))
        val otherKeyEnc = ByteBuffer.wrap(factory.schemeMetadata.encodeAsByteArray(otherKeyPair.public))
        val secret = process<CryptoDerivedSharedSecret>(DeriveSharedSecretCommand(keyEnc, otherKeyEnc, context))
        val operationContextMap = factory.recordedCryptoContexts[context.items[0].value]
        assertNotNull(operationContextMap)
        assertEquals(3, operationContextMap.size)
        assertEquals(context.items[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(context.items[1].value, operationContextMap["reason"])
        assertEquals(tenantId, operationContextMap[CRYPTO_TENANT_ID])
        assertThat(secret.secret.array()).isNotEmpty
    }

    @Test
    fun `Should complete future exceptionally in case of service failure`() {
        val data = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        // generate
        val key1 = process<CryptoPublicKey>(GenerateKeyPairCommand(LEDGER, alias, null, ECDSA_SECP256R1_CODE_NAME, basicContext))
        assertOperationContextMap(basicContext, LEDGER)
        val publicKey = factory.schemeMetadata.decodePublicKey(key1.key.array())
        val info = factory.signingKeyStore.find(tenantId, publicKey)
        assertNotNull(info)
        assertEquals(alias, info.alias)
        // sign using invalid custom scheme
        val encKey = ByteBuffer.wrap(factory.schemeMetadata.encodeAsByteArray(publicKey))
        val css = CryptoSignatureSpec("BAD-SIGNATURE-ALGORITHM", "BAD-DIGEST-ALGORITHM", null)
        val exception = assertThrows<ExecutionException> {
            process(SignRpcCommand(encKey, css, ByteBuffer.wrap(data), KeyValuePairList(emptyList())))
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause).isInstanceOf(IllegalArgumentException::class.java)
    }


    @Test
    fun `Should complete future exceptionally with IllegalArgumentException in case of unknown request`() {
        val exception = assertThrows<ExecutionException> {
            process<Any>(AssignHSMCommand(LEDGER, KeyValuePairList()))
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Should return all supported scheme codes`() {
        val actualSchemes = process<CryptoKeySchemes>(SupportedSchemesRpcQuery(LEDGER))
        val expectedSchemes = factory.signingService.getSupportedSchemes(tenantId, LEDGER)
        assertEquals(expectedSchemes.size, actualSchemes.codes.size)
        expectedSchemes.forEach {
            assertTrue(actualSchemes.codes.contains(it))
        }
    }

    @Test
    fun `Should return all supported scheme codes for fresh keys`() {
        val actualSchemes = process<CryptoKeySchemes>(SupportedSchemesRpcQuery(CI))
        val expectedSchemes = factory.signingService.getSupportedSchemes(
            tenantId,
            LEDGER
        )
        assertEquals(expectedSchemes.size, actualSchemes.codes.size)
        expectedSchemes.forEach {
            assertTrue(actualSchemes.codes.contains(it))
        }
    }

    private fun testSigning(publicKey: PublicKey, data: ByteArray) {
        // sign using public key and default scheme
        val signatureSpec2 = factory.schemeMetadata.supportedSignatureSpec(
            factory.schemeMetadata.findKeyScheme(publicKey)
        ).first()
        val encKey2 = ByteBuffer.wrap(factory.schemeMetadata.encodeAsByteArray(publicKey))
        val command = SignRpcCommand(encKey2, signatureSpec2.toWire(factory.schemeMetadata), ByteBuffer.wrap(data), basicContext)
        val signature2 = process<CryptoSignatureWithKey>(command)
        val operationContextMap = factory.recordedCryptoContexts[basicContext.items[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, basicContext.items.size)
        assertEquals(basicContext.items[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(basicContext.items[1].value, operationContextMap["reason"])
        assertEquals(publicKey, factory.schemeMetadata.decodePublicKey(signature2.publicKey.array()))
        factory.verifier.verify(publicKey, signatureSpec2, signature2.bytes.array(), data)
        // sign using public key and full custom scheme
        val signatureSpec3 = CustomSignatureSpec(
            signatureName = "NONEwithECDSA",
            customDigestName = DigestAlgorithmName.SHA2_512
        )
        val encKey = ByteBuffer.wrap(factory.schemeMetadata.encodeAsByteArray(publicKey))
        val css3 = CryptoSignatureSpec(signatureSpec3.signatureName, signatureSpec3.customDigestName.name, null)
        val signature3 = process<CryptoSignatureWithKey>(SignRpcCommand(encKey, css3, ByteBuffer.wrap(data), emptyContext))
        assertEquals(publicKey, factory.schemeMetadata.decodePublicKey(signature3.publicKey.array()))
        factory.verifier.verify(publicKey, signatureSpec3, signature3.bytes.array(), data)
        // sign using public key and custom scheme
        val signatureSpec4 = SignatureSpec(signatureName = "SHA512withECDSA")
        val css4 = CryptoSignatureSpec(signatureSpec4.signatureName, null, null)
        val signature4 = process<CryptoSignatureWithKey>(SignRpcCommand(encKey, css4, ByteBuffer.wrap(data), emptyContext))
        assertEquals(publicKey, factory.schemeMetadata.decodePublicKey(signature3.publicKey.array()))
        factory.verifier.verify(publicKey, signatureSpec4, signature4.bytes.array(), data)
    }
}