package net.corda.crypto.service.impl.bus.rpc

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.crypto.service.impl.infra.TestServicesFactory.Companion.CTX_TRACKING
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSchemes
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.hsm.registration.commands.AssignHSMCommand
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateWrappingKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignWithSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.queries.ByIdsRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.crypto.wire.ops.rpc.queries.KeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.SupportedSchemesRpcQuery
import net.corda.v5.cipher.suite.CRYPTO_CATEGORY
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoOpsBusProcessorTests {
    companion object {
        private fun getECDSAFullCustomSignatureSpec(): SignatureSpec =
            SignatureSpec(
                signatureName = "NONEwithECDSA",
                customDigestName = DigestAlgorithmName.SHA2_512
            )

        private fun getCustomSignatureSpec(): SignatureSpec =
            SignatureSpec(
                signatureName = "SHA512withECDSA"
            )

        private fun getRSAasCustomSignatureSpecWithParams(): SignatureSpec =
            SignatureSpec(
                signatureName = "RSASSA-PSS",
                params = PSSParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    32,
                    1
                )
            )
    }

    private lateinit var factory: TestServicesFactory
    private lateinit var tenantId: String
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var signingService: SigningService
    private lateinit var signingFactory: SigningServiceFactory
    private lateinit var verifier: SignatureVerificationService
    private lateinit var processor: CryptoOpsBusProcessor

    private fun setup() {
        tenantId = UUID.randomUUID().toString()
        factory = TestServicesFactory()
        schemeMetadata = factory.schemeMetadata
        signingService = factory.createSigningService()
        verifier = factory.verifier
        signingFactory = mock {
            on { getInstance() }.thenReturn(signingService)
        }
        processor = CryptoOpsBusProcessor(
            signingFactory
        )
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

    @Test
    fun `Should return empty list for unknown key id`() {
        setup()
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context,
                ByIdsRpcQuery(listOf(
                    publicKeyIdFromBytes(UUID.randomUUID().toString().toByteArray())
                ))
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertNotNull(result.response)
        assertThat(result.response).isInstanceOf(CryptoSigningKeys::class.java)
        assertEquals(0, (result.response as CryptoSigningKeys).keys.size)
    }

    @Test
    fun `Should return empty list for look up when the filter does not match`() {
        setup()
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context,
                KeysRpcQuery(
                    0,
                    10,
                    CryptoKeyOrderBy.NONE,
                    KeyValuePairList(listOf(KeyValuePair(ALIAS_FILTER, UUID.randomUUID().toString())))
                )
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertNotNull(result.response)
        assertThat(result.response).isInstanceOf(CryptoSigningKeys::class.java)
        assertEquals(0, (result.response as CryptoSigningKeys).keys.size)
    }

    @Test
    fun `Should generate key pair and be able to find and lookup and then sign using default and custom schemes`() {
        setup()
        val data = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        // generate
        val context1 = createRequestContext()
        val operationContext = listOf(
            KeyValuePair(CTX_TRACKING, UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateKeyPairCommand(
                    CryptoConsts.Categories.LEDGER,
                    alias,
                    null,
                    ECDSA_SECP256R1_CODE_NAME,
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = factory.recordedCryptoContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(4, operationContextMap.size)
        assertEquals(operationContext[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertEquals(tenantId, operationContextMap[CRYPTO_TENANT_ID])
        assertEquals(CryptoConsts.Categories.LEDGER, operationContextMap[CRYPTO_CATEGORY])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response).isInstanceOf(CryptoPublicKey::class.java)
        val publicKey = schemeMetadata.decodePublicKey((result1.response as CryptoPublicKey).key.array())
        val info = factory.getSigningCachedKey(tenantId, publicKey)
        assertNotNull(info)
        assertEquals(alias, info.alias)
        // find
        val context2 = createRequestContext()
        val future2 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context2,
                ByIdsRpcQuery(listOf(
                    publicKeyIdFromBytes(info.publicKey)
                ))
            ),
            future2
        )
        val result2 = future2.get()
        assertResponseContext(context2, result2.context)
        assertThat(result2.response).isInstanceOf(CryptoSigningKeys::class.java)
        val key = result2.response as CryptoSigningKeys
        assertEquals(1, key.keys.size)
        assertEquals(publicKey, schemeMetadata.decodePublicKey(key.keys[0].publicKey.array()))
        // lookup
        val context3 = createRequestContext()
        val future3 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context3,
                KeysRpcQuery(
                    0,
                    20,
                    CryptoKeyOrderBy.NONE,
                    KeyValuePairList(listOf(KeyValuePair(ALIAS_FILTER, alias)))
                )
            ),
            future3
        )
        val result3 = future3.get()
        assertResponseContext(context3, result3.context)
        assertThat(result3.response).isInstanceOf(CryptoSigningKeys::class.java)
        val key3 = result3.response as CryptoSigningKeys
        assertEquals(1, key3.keys.size)
        assertEquals(publicKey, schemeMetadata.decodePublicKey(key3.keys[0].publicKey.array()))
        // signing
        testSigningByPublicKeyLookup(publicKey, data)
    }

    @Test
    fun `Should generate key pair and be able to find and lookup and then sign custom signature params`() {
        setup()
        val signatureSpec4 = getRSAasCustomSignatureSpecWithParams()
        val data = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        // generate
        val context1 = createRequestContext()
        val operationContext = listOf(
            KeyValuePair(CTX_TRACKING, UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateKeyPairCommand(
                    CryptoConsts.Categories.LEDGER,
                    alias,
                    null,
                    RSA_CODE_NAME,
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = factory.recordedCryptoContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(4, operationContextMap.size)
        assertEquals(operationContext[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertEquals(tenantId, operationContextMap[CRYPTO_TENANT_ID])
        assertEquals(CryptoConsts.Categories.LEDGER, operationContextMap[CRYPTO_CATEGORY])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response).isInstanceOf(CryptoPublicKey::class.java)
        val publicKey = schemeMetadata.decodePublicKey((result1.response as CryptoPublicKey).key.array())
        val info = factory.getSigningCachedKey(tenantId, publicKey)
        assertNotNull(info)
        assertEquals(alias, info.alias)
        // find
        val context2 = createRequestContext()
        val future2 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context2,
                ByIdsRpcQuery(listOf(
                    publicKeyIdFromBytes(info.publicKey)
                ))
            ),
            future2
        )
        val result2 = future2.get()
        assertResponseContext(context2, result2.context)
        assertThat(result2.response).isInstanceOf(CryptoSigningKeys::class.java)
        val key = result2.response as CryptoSigningKeys
        assertEquals(1, key.keys.size)
        assertEquals(publicKey, schemeMetadata.decodePublicKey(key.keys[0].publicKey.array()))
        // lookup
        val context3 = createRequestContext()
        val future3 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context3,
                KeysRpcQuery(
                    0,
                    20,
                    CryptoKeyOrderBy.NONE,
                    KeyValuePairList(listOf(KeyValuePair(ALIAS_FILTER, alias)))
                )
            ),
            future3
        )
        val result3 = future3.get()
        assertResponseContext(context3, result3.context)
        assertThat(result3.response).isInstanceOf(CryptoSigningKeys::class.java)
        val key3 = result3.response as CryptoSigningKeys
        assertEquals(1, key3.keys.size)
        assertEquals(publicKey, schemeMetadata.decodePublicKey(key3.keys[0].publicKey.array()))
        //
        val context4 = createRequestContext()
        val future4= CompletableFuture<RpcOpsResponse>()
        val serializedParams4 = schemeMetadata.serialize(signatureSpec4.params!!)
        processor.onNext(
            RpcOpsRequest(
                context4,
                SignWithSpecRpcCommand(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    CryptoSignatureSpec(
                        signatureSpec4.signatureName,
                        signatureSpec4.customDigestName?.name,
                        CryptoSignatureParameterSpec(
                            serializedParams4.clazz,
                            ByteBuffer.wrap(serializedParams4.bytes)
                        )
                    ),
                    ByteBuffer.wrap(data),
                    KeyValuePairList(emptyList())
                )
            ),
            future4
        )
        val result4 = future4.get()
        assertResponseContext(context4, result4.context)
        assertThat(result4.response).isInstanceOf(CryptoSignatureWithKey::class.java)
        val signature4 = result4.response as CryptoSignatureWithKey
        assertEquals(publicKey, schemeMetadata.decodePublicKey(signature4.publicKey.array()))
        verifier.verify(publicKey, signatureSpec4, signature4.bytes.array(), data)
    }

    @Test
    fun `Should generate fresh key pair without external id and be able to sign using default and custom schemes`() {
        setup()
        val data = UUID.randomUUID().toString().toByteArray()
        // generate
        val context1 = createRequestContext()
        val operationContext = listOf(
            KeyValuePair(CTX_TRACKING, UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateFreshKeyRpcCommand(
                    null,
                    ECDSA_SECP256R1_CODE_NAME,
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = factory.recordedCryptoContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(4, operationContextMap.size)
        assertEquals(operationContext[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertEquals(tenantId, operationContextMap[CRYPTO_TENANT_ID])
        assertEquals(CryptoConsts.Categories.FRESH_KEYS, operationContextMap[CRYPTO_CATEGORY])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response).isInstanceOf(CryptoPublicKey::class.java)
        val publicKey = schemeMetadata.decodePublicKey((result1.response as CryptoPublicKey).key.array())
        val info = factory.getSigningCachedKey(tenantId, publicKey)
        assertNotNull(info)
        assertNull(info.alias)
        assertNull(info.externalId)
        // signing
        testSigningByPublicKeyLookup(publicKey, data)
    }

    @Test
    fun `Should generate fresh key pair with external id and be able to sign using default and custom schemes`() {
        setup()
        val data = UUID.randomUUID().toString().toByteArray()
        // generate
        val context1 = createRequestContext()
        val operationContext = listOf(
            KeyValuePair(CTX_TRACKING, UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val externalId = UUID.randomUUID()
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateFreshKeyRpcCommand(
                    externalId.toString(),
                    ECDSA_SECP256R1_CODE_NAME,
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = factory.recordedCryptoContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(4, operationContextMap.size)
        assertEquals(operationContext[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertEquals(tenantId, operationContextMap[CRYPTO_TENANT_ID])
        assertEquals(CryptoConsts.Categories.FRESH_KEYS, operationContextMap[CRYPTO_CATEGORY])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response).isInstanceOf(CryptoPublicKey::class.java)
        val publicKey = schemeMetadata.decodePublicKey((result1.response as CryptoPublicKey).key.array())
        val info = factory.getSigningCachedKey(tenantId, publicKey)
        assertNotNull(info)
        assertNull(info.alias)
        assertEquals(externalId, UUID.fromString(info.externalId))
        // signing
        testSigningByPublicKeyLookup(publicKey, data)
    }

    @Test
    fun `Should generate wrapping key`() {
        setup()
        val context1 = createRequestContext()
        val operationContext = listOf(
            KeyValuePair(CTX_TRACKING, UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!"),
            KeyValuePair(CRYPTO_TENANT_ID, tenantId)
        )
        val configId = UUID.randomUUID().toString()
        val masterKeyAlias = UUID.randomUUID().toString()
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateWrappingKeyRpcCommand(
                    configId,
                    masterKeyAlias,
                    true,
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = factory.recordedCryptoContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(3, operationContextMap.size)
        assertEquals(operationContext[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertEquals(tenantId, operationContextMap[CRYPTO_TENANT_ID])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response).isInstanceOf(CryptoNoContentValue::class.java)
        assertThat(factory.softCache.keys).containsKey(masterKeyAlias)
    }

    @Test
    fun `Should complete future exceptionally in case of service failure`() {
        setup()
        val data = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        // generate
        val context1 = createRequestContext()
        val operationContext = listOf(
            KeyValuePair(CTX_TRACKING, UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateKeyPairCommand(
                    CryptoConsts.Categories.LEDGER,
                    alias,
                    null,
                    ECDSA_SECP256R1_CODE_NAME,
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = factory.recordedCryptoContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(4, operationContextMap.size)
        assertEquals(operationContext[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertEquals(tenantId, operationContextMap[CRYPTO_TENANT_ID])
        assertEquals(CryptoConsts.Categories.LEDGER, operationContextMap[CRYPTO_CATEGORY])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response).isInstanceOf(CryptoPublicKey::class.java)
        val publicKey = schemeMetadata.decodePublicKey((result1.response as CryptoPublicKey).key.array())
        val info = factory.getSigningCachedKey(tenantId, publicKey)
        assertNotNull(info)
        assertEquals(alias, info.alias)
        // sign using invalid custom scheme
        val context3 = createRequestContext()
        val future3 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context3,
                SignWithSpecRpcCommand(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    CryptoSignatureSpec(
                        "BAD-SIGNATURE-ALGORITHM",
                        "BAD-DIGEST-ALGORITHM",
                        null
                    ),
                    ByteBuffer.wrap(data),
                    KeyValuePairList(emptyList())
                )
            ),
            future3
        )
        val exception = assertThrows<ExecutionException> {
            future3.get()
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause).isInstanceOf(CryptoServiceLibraryException::class.java)
    }

    @Test
    fun `Should complete future exceptionally in case of unknown request`() {
        setup()
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context,
                AssignHSMCommand(CryptoConsts.Categories.LEDGER, KeyValuePairList())
            ),
            future
        )
        val exception = assertThrows<ExecutionException> {
            future.get()
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause).isInstanceOf(CryptoServiceLibraryException::class.java)
        assertThat(exception.cause?.cause).isInstanceOf(CryptoServiceBadRequestException::class.java)
    }

    @Test
    fun `Should return all supported scheme codes`() {
        setup()
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context,
                SupportedSchemesRpcQuery(
                    CryptoConsts.Categories.LEDGER
                )
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response).isInstanceOf(CryptoSignatureSchemes::class.java)
        val actualSchemes = result.response as CryptoSignatureSchemes
        val expectedSchemes = signingService.getSupportedSchemes(
            tenantId,
            CryptoConsts.Categories.LEDGER
        )
        assertEquals(expectedSchemes.size, actualSchemes.codes.size)
        expectedSchemes.forEach {
            assertTrue(actualSchemes.codes.contains(it))
        }
    }

    @Test
    fun `Should return all supported scheme codes for fresh keys`() {
        setup()
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context,
                SupportedSchemesRpcQuery(
                    CryptoConsts.Categories.FRESH_KEYS
                )
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response).isInstanceOf(CryptoSignatureSchemes::class.java)
        val actualSchemes = result.response as CryptoSignatureSchemes
        val expectedSchemes = signingService.getSupportedSchemes(
            tenantId,
            CryptoConsts.Categories.LEDGER
        )
        assertEquals(expectedSchemes.size, actualSchemes.codes.size)
        expectedSchemes.forEach {
            assertTrue(actualSchemes.codes.contains(it))
        }
    }

    private fun testSigningByPublicKeyLookup(publicKey: PublicKey, data: ByteArray) {
        // sign using public key and default scheme
        val context2 = createRequestContext()
        val operationContext = listOf(
            KeyValuePair(CTX_TRACKING, UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future2 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context2,
                SignRpcCommand(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    ByteBuffer.wrap(data),
                    KeyValuePairList(operationContext)
                )
            ),
            future2
        )
        val result2 = future2.get()
        val operationContextMap = factory.recordedCryptoContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap[CTX_TRACKING])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertResponseContext(context2, result2.context)
        assertThat(result2.response).isInstanceOf(CryptoSignatureWithKey::class.java)
        val signature2 = result2.response as CryptoSignatureWithKey
        assertEquals(publicKey, schemeMetadata.decodePublicKey(signature2.publicKey.array()))
        verifier.verify(publicKey, signature2.bytes.array(), data)
        // sign using public key and full custom scheme
        val signatureSpec3 = getECDSAFullCustomSignatureSpec()
        val context3 = createRequestContext()
        val future3 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context3,
                SignWithSpecRpcCommand(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    CryptoSignatureSpec(
                        signatureSpec3.signatureName,
                        signatureSpec3.customDigestName?.name,
                        null
                    ),
                    ByteBuffer.wrap(data),
                    KeyValuePairList(emptyList())
                )
            ),
            future3
        )
        val result3 = future3.get()
        assertResponseContext(context3, result3.context)
        assertThat(result3.response).isInstanceOf(CryptoSignatureWithKey::class.java)
        val signature3 = result3.response as CryptoSignatureWithKey
        assertEquals(publicKey, schemeMetadata.decodePublicKey(signature3.publicKey.array()))
        verifier.verify(publicKey, signatureSpec3, signature3.bytes.array(), data)
        // sign using public key and custom scheme
        val signatureSpec4 = getCustomSignatureSpec()
        val context4 = createRequestContext()
        val future4 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context4,
                SignWithSpecRpcCommand(
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                    CryptoSignatureSpec(
                        signatureSpec4.signatureName,
                        signatureSpec4.customDigestName?.name,
                        null
                    ),
                    ByteBuffer.wrap(data),
                    KeyValuePairList(emptyList())
                )
            ),
            future4
        )
        val result4 = future4.get()
        assertResponseContext(context4, result4.context)
        assertThat(result4.response).isInstanceOf(CryptoSignatureWithKey::class.java)
        val signature4 = result4.response as CryptoSignatureWithKey
        assertEquals(publicKey, schemeMetadata.decodePublicKey(signature3.publicKey.array()))
        verifier.verify(publicKey, signatureSpec4, signature4.bytes.array(), data)
    }
}