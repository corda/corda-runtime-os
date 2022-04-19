package net.corda.crypto.service.impl.rpc

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSchemes
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignWithSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.queries.ByIdsRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.crypto.wire.ops.rpc.queries.KeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.SupportedSchemesRpcQuery
import net.corda.data.crypto.wire.registration.hsm.AssignHSMCommand
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoOpsRpcProcessorTests {
    private class SigningServiceWrapper(private val impl: SigningService) : SigningService by impl {
        companion object {
            val recordedContexts = ConcurrentHashMap<String, Map<String, String>>()
        }

        override fun generateKeyPair(
            tenantId: String,
            category: String,
            alias: String,
            context: Map<String, String>
        ): PublicKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.generateKeyPair(tenantId, category, alias, context)
        }

        override fun generateKeyPair(
            tenantId: String,
            category: String,
            alias: String,
            externalId: String,
            context: Map<String, String>
        ): PublicKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.generateKeyPair(tenantId, category, alias, externalId, context)
        }

        override fun freshKey(tenantId: String, context: Map<String, String>): PublicKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.freshKey(tenantId, context)
        }

        override fun freshKey(tenantId: String, externalId: String, context: Map<String, String>): PublicKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.freshKey(tenantId, externalId, context)
        }

        override fun sign(
            tenantId: String,
            publicKey: PublicKey,
            data: ByteArray,
            context: Map<String, String>
        ): DigitalSignature.WithKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.sign(tenantId, publicKey, data, context)
        }

        override fun sign(
            tenantId: String,
            publicKey: PublicKey,
            signatureSpec: SignatureSpec,
            data: ByteArray,
            context: Map<String, String>
        ): DigitalSignature.WithKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.sign(tenantId, publicKey, signatureSpec, data, context)
        }
    }

    companion object {
        private fun getFullCustomSignatureSpec(): SignatureSpec =
            SignatureSpec(
                signatureName = "NONEwithECDSA",
                customDigestName = DigestAlgorithmName.SHA2_512
            )

        private fun getCustomSignatureSpec(): SignatureSpec =
            SignatureSpec(
                signatureName = "SHA512withECDSA"
            )

        private fun getCustomSignatureSpecWithParams(): SignatureSpec =
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
    private lateinit var processor: CryptoOpsRpcProcessor

    private fun setup(schemeCode: String = ECDSA_SECP256R1_CODE_NAME) {
        tenantId = UUID.randomUUID().toString()
        factory = TestServicesFactory()
        schemeMetadata = factory.schemeMetadata
        signingService = factory.createSigningService(
            schemeMetadata.findSignatureScheme(schemeCode)
        )
        verifier = factory.verifier
        signingFactory = mock {
            on { getInstance() }.thenReturn(SigningServiceWrapper(signingService))
        }
        processor = CryptoOpsRpcProcessor(
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
        assertThat(
            actual.responseTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(expected.requestTimestamp.epochSecond), lessThanOrEqualTo(now.epochSecond))
        )
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
                    publicKeyIdOf(UUID.randomUUID().toString().toByteArray())
                ))
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertNotNull(result.response)
        assertThat(result.response, instanceOf(CryptoSigningKeys::class.java))
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
        assertThat(result.response, instanceOf(CryptoSigningKeys::class.java))
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
            KeyValuePair("someId", UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateKeyPairCommand(
                    CryptoConsts.HsmCategories.LEDGER,
                    alias,
                    null,
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = SigningServiceWrapper.recordedContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap["someId"])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response, instanceOf(CryptoPublicKey::class.java))
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
                    publicKeyIdOf(info.publicKey)
                ))
            ),
            future2
        )
        val result2 = future2.get()
        assertResponseContext(context2, result2.context)
        assertThat(result2.response, instanceOf(CryptoSigningKeys::class.java))
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
        assertThat(result3.response, instanceOf(CryptoSigningKeys::class.java))
        val key3 = result3.response as CryptoSigningKeys
        assertEquals(1, key3.keys.size)
        assertEquals(publicKey, schemeMetadata.decodePublicKey(key3.keys[0].publicKey.array()))
        // signing
        testSigningByPublicKeyLookup(publicKey, data)
    }

    @Test
    fun `Should generate key pair and be able to find and lookup and then sign custom signature params`() {
        setup(schemeCode = RSA_CODE_NAME)
        val data = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        // generate
        val context1 = createRequestContext()
        val operationContext = listOf(
            KeyValuePair("someId", UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateKeyPairCommand(
                    CryptoConsts.HsmCategories.LEDGER,
                    alias,
                    null,
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = SigningServiceWrapper.recordedContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap["someId"])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response, instanceOf(CryptoPublicKey::class.java))
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
                    publicKeyIdOf(info.publicKey)
                ))
            ),
            future2
        )
        val result2 = future2.get()
        assertResponseContext(context2, result2.context)
        assertThat(result2.response, instanceOf(CryptoSigningKeys::class.java))
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
        assertThat(result3.response, instanceOf(CryptoSigningKeys::class.java))
        val key3 = result3.response as CryptoSigningKeys
        assertEquals(1, key3.keys.size)
        assertEquals(publicKey, schemeMetadata.decodePublicKey(key3.keys[0].publicKey.array()))
        //
        val signatureSpec4 = getCustomSignatureSpecWithParams()
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
        assertThat(result4.response, instanceOf(CryptoSignatureWithKey::class.java))
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
            KeyValuePair("someId", UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateFreshKeyRpcCommand(
                    null,
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = SigningServiceWrapper.recordedContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap["someId"])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response, instanceOf(CryptoPublicKey::class.java))
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
            KeyValuePair("someId", UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val externalId = UUID.randomUUID()
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateFreshKeyRpcCommand(
                    externalId.toString(),
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = SigningServiceWrapper.recordedContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap["someId"])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response, instanceOf(CryptoPublicKey::class.java))
        val publicKey = schemeMetadata.decodePublicKey((result1.response as CryptoPublicKey).key.array())
        val info = factory.getSigningCachedKey(tenantId, publicKey)
        assertNotNull(info)
        assertNull(info.alias)
        assertEquals(externalId, UUID.fromString(info.externalId))
        // signing
        testSigningByPublicKeyLookup(publicKey, data)
    }

    @Test
    fun `Should complete future exceptionally in case of service failure`() {
        setup()
        val data = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        // generate
        val context1 = createRequestContext()
        val operationContext = listOf(
            KeyValuePair("someId", UUID.randomUUID().toString()),
            KeyValuePair("reason", "Hello World!")
        )
        val future1 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context1,
                GenerateKeyPairCommand(
                    CryptoConsts.HsmCategories.LEDGER,
                    alias,
                    null,
                    KeyValuePairList(operationContext)
                )
            ),
            future1
        )
        val result1 = future1.get()
        val operationContextMap = SigningServiceWrapper.recordedContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap["someId"])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertResponseContext(context1, result1.context)
        assertThat(result1.response, instanceOf(CryptoPublicKey::class.java))
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
        assertThat(exception.cause, instanceOf(CryptoServiceLibraryException::class.java))
    }

    @Test
    fun `Should complete future exceptionally in case of unknown request`() {
        setup()
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context,
                AssignHSMCommand(
                    CryptoConsts.HsmCategories.LEDGER,
                    RSA_CODE_NAME,
                    KeyValuePairList(emptyList())
                )
            ),
            future
        )
        val exception = assertThrows<ExecutionException> {
            future.get()
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(CryptoServiceLibraryException::class.java))
        assertThat(exception.cause, instanceOf(CryptoServiceLibraryException::class.java))
        assertThat(exception.cause?.cause, instanceOf(CryptoServiceBadRequestException::class.java))
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
                    CryptoConsts.HsmCategories.LEDGER
                )
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response, instanceOf(CryptoSignatureSchemes::class.java))
        val actualSchemes = result.response as CryptoSignatureSchemes
        val expectedSchemes = signingService.getSupportedSchemes(
            tenantId,
            CryptoConsts.HsmCategories.LEDGER
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
                    CryptoConsts.HsmCategories.FRESH_KEYS
                )
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response, instanceOf(CryptoSignatureSchemes::class.java))
        val actualSchemes = result.response as CryptoSignatureSchemes
        val expectedSchemes = signingService.getSupportedSchemes(
            tenantId,
            CryptoConsts.HsmCategories.LEDGER
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
            KeyValuePair("someId", UUID.randomUUID().toString()),
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
        val operationContextMap = SigningServiceWrapper.recordedContexts[operationContext[0].value]
        assertNotNull(operationContextMap)
        assertEquals(2, operationContext.size)
        assertEquals(operationContext[0].value, operationContextMap["someId"])
        assertEquals(operationContext[1].value, operationContextMap["reason"])
        assertResponseContext(context2, result2.context)
        assertThat(result2.response, instanceOf(CryptoSignatureWithKey::class.java))
        val signature2 = result2.response as CryptoSignatureWithKey
        assertEquals(publicKey, schemeMetadata.decodePublicKey(signature2.publicKey.array()))
        verifier.verify(publicKey, signature2.bytes.array(), data)
        // sign using public key and full custom scheme
        val signatureSpec3 = getFullCustomSignatureSpec()
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
        assertThat(result3.response, instanceOf(CryptoSignatureWithKey::class.java))
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
        assertThat(result4.response, instanceOf(CryptoSignatureWithKey::class.java))
        val signature4 = result4.response as CryptoSignatureWithKey
        assertEquals(publicKey, schemeMetadata.decodePublicKey(signature3.publicKey.array()))
        verifier.verify(publicKey, signatureSpec4, signature4.bytes.array(), data)
    }
}