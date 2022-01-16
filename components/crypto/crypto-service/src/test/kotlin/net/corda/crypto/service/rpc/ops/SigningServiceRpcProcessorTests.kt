package net.corda.crypto.service.rpc.ops

import net.corda.crypto.CryptoConsts
import net.corda.crypto.service.generateKeyPair
import net.corda.crypto.service.signing.CryptoServicesTestFactory
import net.corda.crypto.service.signing.SigningService
import net.corda.crypto.service.signing.SigningServiceFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignature
import net.corda.data.crypto.wire.CryptoSignatureSchemes
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.rpc.FilterMyKeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.PublicKeyRpcQuery
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithAliasRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithAliasSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SupportedSchemesRpcQuery
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
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SigningServiceRpcProcessorTests {
    private class SigningServiceWrapper(private val impl: SigningService) : SigningService by impl {
        companion object {
            val recordedContexts = ConcurrentHashMap<String, Map<String, String>>()
        }

        override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> =
            if(candidateKeys.any()) {
                listOf(candidateKeys.first())
            } else {
                emptyList()
            }

        override fun generateKeyPair(
            category: String,
            alias: String,
            context: Map<String, String>
        ): PublicKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.generateKeyPair(category, alias, context)
        }

        override fun freshKey(context: Map<String, String>): PublicKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.freshKey(context)
        }

        override fun freshKey(externalId: UUID, context: Map<String, String>): PublicKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.freshKey(externalId, context)
        }

        override fun sign(
            publicKey: PublicKey,
            data: ByteArray,
            context: Map<String, String>
        ): DigitalSignature.WithKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.sign(publicKey, data, context)
        }

        override fun sign(
            publicKey: PublicKey,
            signatureSpec: SignatureSpec,
            data: ByteArray,
            context: Map<String, String>
        ): DigitalSignature.WithKey {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.sign(publicKey, signatureSpec, data, context)
        }

        override fun sign(alias: String, data: ByteArray, context: Map<String, String>): ByteArray {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.sign(alias, data, context)
        }

        override fun sign(
            alias: String,
            signatureSpec: SignatureSpec,
            data: ByteArray,
            context: Map<String, String>
        ): ByteArray {
            if (context.containsKey("someId")) {
                recordedContexts[context.getValue("someId")] = context
            }
            return impl.sign(alias, signatureSpec, data, context)
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
    }
    private lateinit var tenantId: String
    private lateinit var factory: CryptoServicesTestFactory
    private lateinit var services: CryptoServicesTestFactory.CryptoServices
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var signingService: SigningService
    private lateinit var signingFactory: SigningServiceFactory
    private lateinit var verifier: SignatureVerificationService
    private lateinit var processor: CryptoOpsRpcProcessor

    fun setup(category: String) {
        factory = CryptoServicesTestFactory()
        services = factory.createCryptoServices(
            category = category
        )
        tenantId = services.tenantId
        schemeMetadata = factory.getSchemeMap()
        signingService = services.createSigningService(
            schemeMetadata.findSignatureScheme(ECDSA_SECP256R1_CODE_NAME)
        )
        verifier = factory.getSignatureVerificationService()
        signingFactory = mock {
            on { getInstance(tenantId) }.thenReturn(SigningServiceWrapper(signingService))
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
    @Timeout(5)
    fun `Should return CryptoNoContentValue for unknown key alias`() {
        setup(category = CryptoConsts.CryptoCategories.LEDGER)
        val alias = newAlias()
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context,
                PublicKeyRpcQuery(alias)
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertNotNull(result.response)
        assertThat(result.response, instanceOf(CryptoNoContentValue::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should filer my keys`() {
        setup(category = CryptoConsts.CryptoCategories.LEDGER)
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        val candidates = listOf<PublicKey>(
            generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public,
            generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public
        )
        processor.onNext(
            RpcOpsRequest(
                context,
                FilterMyKeysRpcQuery(candidates.map {
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(it))
                })
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertNotNull(result.response)
        assertThat(result.response, instanceOf(CryptoPublicKeys::class.java))
        val keys = result.response as CryptoPublicKeys
        assertEquals(1, keys.keys.size)
        assertEquals(candidates[0], schemeMetadata.decodePublicKey(keys.keys[0].array()))
    }

    @Test
    @Timeout(5)
    fun `Should generate key pair and be able to find and then sign using default and custom schemes`() {
        setup(category = CryptoConsts.CryptoCategories.LEDGER)
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
                    CryptoConsts.CryptoCategories.LEDGER,
                    alias,
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
        val info = services.getSigningKeyRecord(publicKey)
        assertNotNull(info)
        assertEquals(alias, info.alias)
        // find
        val context2 = createRequestContext()
        val future2 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context2,
                PublicKeyRpcQuery(alias)
            ),
            future2
        )
        val result2 = future2.get()
        assertResponseContext(context2, result2.context)
        assertThat(result2.response, instanceOf(CryptoPublicKey::class.java))
        val key = result2.response as CryptoPublicKey
        assertEquals(publicKey, schemeMetadata.decodePublicKey(key.key.array()))
        // signing
        testSigningByPublicKeyLookup(publicKey, data)
        testSigningByAliasLookup(alias, publicKey, data)
    }

    @Test
    @Timeout(5)
    fun `Should generate fresh key pair without external id and be able to sign using default and custom schemes`() {
        setup(category = CryptoConsts.CryptoCategories.LEDGER)
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
        val info = services.getSigningKeyRecord(publicKey)
        assertNotNull(info)
        assertNull(info.alias)
        assertNull(info.externalId)
        // signing
        testSigningByPublicKeyLookup(publicKey, data)
    }

    @Test
    @Timeout(5)
    fun `Should generate fresh key pair with external id and be able to sign using default and custom schemes`() {
        setup(category = CryptoConsts.CryptoCategories.LEDGER)
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
        val info = services.getSigningKeyRecord(publicKey)
        assertNotNull(info)
        assertNull(info.alias)
        assertEquals(externalId, info.externalId)
        // signing
        testSigningByPublicKeyLookup(publicKey, data)
    }

    @Test
    @Timeout(5)
    fun `Should complete future exceptionally in case of service failure`() {
        setup(category = CryptoConsts.CryptoCategories.LEDGER)
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
                    CryptoConsts.CryptoCategories.LEDGER,
                    alias,
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
        val info = services.getSigningKeyRecord(publicKey)
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
                        "BAD-DIGEST-ALGORITHM"
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
    @Timeout(5)
    fun `Should complete future exceptionally in case of unknown request`() {
        setup(category = CryptoConsts.CryptoCategories.LEDGER)
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context,
                AssignHSMCommand(
                    CryptoConsts.CryptoCategories.LEDGER,
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
    @Timeout(5)
    fun `Should return all supported scheme codes`() {
        setup(category = CryptoConsts.CryptoCategories.LEDGER)
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context,
                SupportedSchemesRpcQuery(
                    CryptoConsts.CryptoCategories.LEDGER
                )
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response, instanceOf(CryptoSignatureSchemes::class.java))
        val actualSchemes = result.response as CryptoSignatureSchemes
        val expectedSchemes = signingService.getSupportedSchemes(
            CryptoConsts.CryptoCategories.LEDGER
        )
        assertEquals(expectedSchemes.size, actualSchemes.codes.size)
        expectedSchemes.forEach {
            assertTrue(actualSchemes.codes.contains(it))
        }
    }

    @Test
    @Timeout(5)
    fun `Should return all supported scheme codes for fresh keys`() {
        setup(category = CryptoConsts.CryptoCategories.FRESH_KEYS)
        val context = createRequestContext()
        val future = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context,
                SupportedSchemesRpcQuery(
                    CryptoConsts.CryptoCategories.FRESH_KEYS
                )
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response, instanceOf(CryptoSignatureSchemes::class.java))
        val actualSchemes = result.response as CryptoSignatureSchemes
        val expectedSchemes = signingService.getSupportedSchemes(
            CryptoConsts.CryptoCategories.LEDGER
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
                    CryptoSignatureSpec(signatureSpec3.signatureName, signatureSpec3.customDigestName?.name),
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
                    CryptoSignatureSpec(signatureSpec4.signatureName, signatureSpec4.customDigestName?.name),
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

    private fun testSigningByAliasLookup(alias: String, publicKey: PublicKey, data: ByteArray) {
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
                SignWithAliasRpcCommand(
                    alias,
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
        assertThat(result2.response, instanceOf(CryptoSignature::class.java))
        val signature2 = result2.response as CryptoSignature
        verifier.verify(publicKey, signature2.bytes.array(), data)
        // sign using public key and full custom scheme
        val signatureSpec3 = getFullCustomSignatureSpec()
        val context3 = createRequestContext()
        val future3 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context3,
                SignWithAliasSpecRpcCommand(
                    alias,
                    CryptoSignatureSpec(signatureSpec3.signatureName, signatureSpec3.customDigestName?.name),
                    ByteBuffer.wrap(data),
                    KeyValuePairList(emptyList())
                )
            ),
            future3
        )
        val result3 = future3.get()
        assertResponseContext(context3, result3.context)
        assertThat(result3.response, instanceOf(CryptoSignature::class.java))
        val signature3 = result3.response as CryptoSignature
        verifier.verify(publicKey, signatureSpec3, signature3.bytes.array(), data)
        // sign using public key and custom scheme
        val signatureSpec4 = getCustomSignatureSpec()
        val context4 = createRequestContext()
        val future4 = CompletableFuture<RpcOpsResponse>()
        processor.onNext(
            RpcOpsRequest(
                context4,
                SignWithAliasSpecRpcCommand(
                    alias,
                    CryptoSignatureSpec(signatureSpec4.signatureName, signatureSpec4.customDigestName?.name),
                    ByteBuffer.wrap(data),
                    KeyValuePairList(emptyList())
                )
            ),
            future4
        )
        val result4 = future4.get()
        assertResponseContext(context4, result4.context)
        assertThat(result4.response, instanceOf(CryptoSignature::class.java))
        val signature4 = result4.response as CryptoSignature
        verifier.verify(publicKey, signatureSpec4, signature4.bytes.array(), data)
    }
}