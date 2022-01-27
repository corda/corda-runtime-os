package net.corda.crypto.client.impl

import net.corda.crypto.CryptoConsts
import net.corda.crypto.impl.CipherSchemeMetadataImpl
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignature
import net.corda.data.crypto.wire.CryptoSignatureSchemes
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.rpc.AssignedHSMRpcQuery
import net.corda.data.crypto.wire.ops.rpc.FilterMyKeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.HSMKeyDetails
import net.corda.data.crypto.wire.ops.rpc.HSMKeyInfoByAliasRpcQuery
import net.corda.data.crypto.wire.ops.rpc.HSMKeyInfoByPublicKeyRpcQuery
import net.corda.data.crypto.wire.ops.rpc.PublicKeyRpcQuery
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithAliasRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithAliasSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SupportedSchemesRpcQuery
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.core.IsInstanceOf
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertTrue

class CryptoOpsClientComponentTests : ComponentTestsBase<CryptoOpsClientComponent>() {
    private lateinit var knownTenantId: String
    private lateinit var knownAlias: String
    private lateinit var knownOperationContext: Map<String, String>
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var sender: RPCSender<RpcOpsRequest, RpcOpsResponse>
    private lateinit var publisherFactory: PublisherFactory

    @BeforeEach
    fun setup() {
        super.setup {
            knownTenantId = UUID.randomUUID().toString()
            knownAlias = UUID.randomUUID().toString()
            knownOperationContext = mapOf(
                UUID.randomUUID().toString() to UUID.randomUUID().toString()
            )
            schemeMetadata = CipherSchemeMetadataImpl()
            sender = mock()
            publisherFactory = mock {
                on { createRPCSender<RpcOpsRequest, RpcOpsResponse>(any(), any()) } doReturn sender
            }
            CryptoOpsClientComponent(
                coordinatorFactory = coordinatorFactory,
                publisherFactory = publisherFactory,
                suiteFactory = object : CipherSuiteFactory {
                    override fun getDigestService(): DigestService =
                        throw NotImplementedError()
                    override fun getSchemeMap(): CipherSchemeMetadata =
                        schemeMetadata
                    override fun getSignatureVerificationService(): SignatureVerificationService =
                        throw NotImplementedError()
                }
            )
        }
    }

    private fun setupCompletedResponse(respFactory: (RpcOpsRequest) -> Any) {
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, RpcOpsRequest::class.java)
            val future = CompletableFuture<RpcOpsResponse>()
            future.complete(
                RpcOpsResponse(
                    CryptoResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        req.context.tenantId,
                        req.context.other
                    ), respFactory(req)
                )
            )
            future
        }
    }

    private fun assertRequestContext(result: SendActResult<RpcOpsRequest, *>) {
        val context = result.firstRequest.context
        kotlin.test.assertEquals(knownTenantId, context.tenantId)
        result.assertThatIsBetween(context.requestTimestamp)
        kotlin.test.assertEquals(CryptoOpsClientImpl::class.simpleName, context.requestingComponent)
        assertThat(context.other.items, empty())
    }

    private fun assertOperationContext(context: KeyValuePairList) {
        assertNotNull(context.items)
        assertEquals(1, context.items.size)
        knownOperationContext.forEach {
            assertTrue(context.items.any { c -> it.key == c.key && it.value == c.value})
        }
    }

    private inline fun <reified OP> assertOperationType(result: SendActResult<RpcOpsRequest, *>): OP {
        assertNotNull(result.firstRequest.request)
        assertThat(result.firstRequest.request, IsInstanceOf(OP::class.java))
        return result.firstRequest.request as OP
    }

    @Test
    @Timeout(5)
    fun `Should return supported scheme codes`() {
        setupCompletedResponse {
            CryptoSignatureSchemes(
                schemeMetadata.schemes.map { it.codeName }
            )
        }
        val result = sender.act {
            component.getSupportedSchemes(knownTenantId, CryptoConsts.Categories.LEDGER)
        }
        assertNotNull(result.value)
        assertEquals(schemeMetadata.schemes.size, result.value!!.size)
        schemeMetadata.schemes.forEach {
            assertTrue(result.value.contains(it.codeName))
        }
        val query = assertOperationType<SupportedSchemesRpcQuery>(result)
        assertEquals(CryptoConsts.Categories.LEDGER, query.category)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should find public key`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        setupCompletedResponse {
            CryptoPublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val result = sender.act {
            component.findPublicKey(knownTenantId, knownAlias)
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value)
        val query = assertOperationType<PublicKeyRpcQuery>(result)
        assertEquals(knownAlias, query.alias)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should return null when public key is not found`() {
        setupCompletedResponse {
            CryptoNoContentValue()
        }
        val result = sender.act {
            component.findPublicKey(knownTenantId, knownAlias)
        }
        assertNull(result.value)
        val query = assertOperationType<PublicKeyRpcQuery>(result)
        assertEquals(knownAlias, query.alias)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should filter my keys`() {
        val myPublicKeys = listOf(
            generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public,
            generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public
        )
        val notMyKey = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public
        setupCompletedResponse {
            CryptoPublicKeys(
                myPublicKeys.map { ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(it)) }
            )
        }
        val result = sender.act {
            component.filterMyKeys(knownTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        }
        assertNotNull(result.value)
        assertEquals(2, result.value!!.count())
        assertTrue(result.value.any { it == myPublicKeys[0] })
        assertTrue(result.value.any { it == myPublicKeys[1] })
        val query = assertOperationType<FilterMyKeysRpcQuery>(result)
        assertEquals(3, query.keys.size)
        assertTrue(query.keys.any { it.array().contentEquals(schemeMetadata.encodeAsByteArray(myPublicKeys[0])) })
        assertTrue(query.keys.any { it.array().contentEquals(schemeMetadata.encodeAsByteArray(myPublicKeys[1])) })
        assertTrue(query.keys.any { it.array().contentEquals(schemeMetadata.encodeAsByteArray(notMyKey)) })
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should be able to handle empty filter my keys result`() {
        val myPublicKeys = listOf(
            generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public,
            generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public
        )
        val notMyKey = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public
        setupCompletedResponse {
            CryptoPublicKeys(emptyList())
        }
        val result = sender.act {
            component.filterMyKeys(knownTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        }
        assertNotNull(result.value)
        assertEquals(0, result.value!!.count())
        val query = assertOperationType<FilterMyKeysRpcQuery>(result)
        assertEquals(3, query.keys.size)
        assertTrue(query.keys.any { it.array().contentEquals(schemeMetadata.encodeAsByteArray(myPublicKeys[0])) })
        assertTrue(query.keys.any { it.array().contentEquals(schemeMetadata.encodeAsByteArray(myPublicKeys[1])) })
        assertTrue(query.keys.any { it.array().contentEquals(schemeMetadata.encodeAsByteArray(notMyKey)) })
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should generate key pair`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        setupCompletedResponse {
            CryptoPublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val result = sender.act {
            component.generateKeyPair(
                tenantId = knownTenantId,
                category = CryptoConsts.Categories.LEDGER,
                alias = knownAlias,
                context = knownOperationContext
            )
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value)
        val command = assertOperationType<GenerateKeyPairCommand>(result)
        assertEquals(CryptoConsts.Categories.LEDGER, command.category)
        assertEquals(knownAlias, command.alias)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should generate fresh key without external id`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        setupCompletedResponse {
            CryptoPublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val result = sender.act {
            component.freshKey(knownTenantId, knownOperationContext)
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value)
        val command = assertOperationType<GenerateFreshKeyRpcCommand>(result)
        assertNull(command.externalId)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should generate fresh key with external id`() {
        val externalId = UUID.randomUUID()
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        setupCompletedResponse {
            CryptoPublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val result = sender.act {
            component.freshKey(knownTenantId, externalId, knownOperationContext)
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value)
        val command = assertOperationType<GenerateFreshKeyRpcCommand>(result)
        assertNotNull(command.externalId)
        assertEquals(externalId, UUID.fromString(command.externalId))
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should sign by referencing public key`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = signData(schemeMetadata, keyPair, data)
        setupCompletedResponse {
            CryptoSignatureWithKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public)),
                ByteBuffer.wrap(signature)
            )
        }
        val result = sender.act {
            component.sign(knownTenantId, keyPair.public, data, knownOperationContext)
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value!!.by)
        assertArrayEquals(signature, result.value.bytes)
        val command = assertOperationType<SignRpcCommand>(result)
        assertNotNull(command)
        assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPair.public), command.publicKey.array())
        assertArrayEquals(data, command.bytes.array())
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should sign by referencing public key and using custom signature spec`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = signData(schemeMetadata, keyPair, data)
        val spec = SignatureSpec("NONEwithECDSA", DigestAlgorithmName.SHA2_256)
        setupCompletedResponse {
            CryptoSignatureWithKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public)),
                ByteBuffer.wrap(signature)
            )
        }
        val result = sender.act {
            component.sign(knownTenantId, keyPair.public, spec, data, knownOperationContext)
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value!!.by)
        assertArrayEquals(signature, result.value.bytes)
        val command = assertOperationType<SignWithSpecRpcCommand>(result)
        assertNotNull(command)
        assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPair.public), command.publicKey.array())
        assertArrayEquals(data, command.bytes.array())
        assertEquals(spec.signatureName, command.signatureSpec.signatureName)
        assertNotNull(spec.customDigestName)
        assertEquals(spec.customDigestName!!.name, command.signatureSpec.customDigestName)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should sign by referencing key alias`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = signData(schemeMetadata, keyPair, data)
        setupCompletedResponse {
            CryptoSignature(
                ByteBuffer.wrap(signature)
            )
        }
        val result = sender.act {
            component.sign(
                tenantId = knownTenantId,
                alias = knownAlias,
                data = data,
                context = knownOperationContext
            )
        }
        assertNotNull(result.value)
        assertArrayEquals(signature, result.value)
        val command = assertOperationType<SignWithAliasRpcCommand>(result)
        assertNotNull(command)
        assertEquals(knownAlias, command.alias)
        assertArrayEquals(data, command.bytes.array())
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should sign by referencing key alias and using custom signature spec`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val data = UUID.randomUUID().toString().toByteArray()
        val spec = SignatureSpec("NONEwithECDSA", DigestAlgorithmName.SHA2_256)
        val signature = signData(schemeMetadata, keyPair, data)
        setupCompletedResponse {
            CryptoSignature(
                ByteBuffer.wrap(signature)
            )
        }
        val result = sender.act {
            component.sign(
                tenantId = knownTenantId,
                alias = knownAlias,
                signatureSpec = spec,
                data = data,
                context = knownOperationContext
            )
        }
        assertNotNull(result.value)
        assertArrayEquals(signature, result.value)
        val command = assertOperationType<SignWithAliasSpecRpcCommand>(result)
        assertNotNull(command)
        assertEquals(knownAlias, command.alias)
        assertArrayEquals(data, command.bytes.array())
        assertEquals(spec.signatureName, command.signatureSpec.signatureName)
        assertNotNull(spec.customDigestName)
        assertEquals(spec.customDigestName!!.name, command.signatureSpec.customDigestName)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should find key information by referencing public key`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val expectedValue = HSMKeyDetails()
        setupCompletedResponse {
            expectedValue
        }
        val result = sender.act {
            component.findHSMKey(knownTenantId, keyPair.public)
        }
        assertNotNull(result.value)
        assertEquals(expectedValue, result.value)
        val query = assertOperationType<HSMKeyInfoByPublicKeyRpcQuery>(result)
        assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPair.public), query.publicKey.array())
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should return null for key information when public key is not found`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        setupCompletedResponse {
            CryptoNoContentValue()
        }
        val result = sender.act {
            component.findHSMKey(knownTenantId, keyPair.public)
        }
        assertNull(result.value)
        val query = assertOperationType<HSMKeyInfoByPublicKeyRpcQuery>(result)
        assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPair.public), query.publicKey.array())
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should find key information by referencing key alias`() {
        val expectedValue = HSMKeyDetails()
        setupCompletedResponse {
            expectedValue
        }
        val result = sender.act {
            component.findHSMKey(
                tenantId = knownTenantId,
                alias = knownAlias
            )
        }
        assertNotNull(result.value)
        assertEquals(expectedValue, result.value)
        val query = assertOperationType<HSMKeyInfoByAliasRpcQuery>(result)
        assertEquals(knownAlias, query.alias)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should return null for key information when key alias is not found`() {
        setupCompletedResponse {
            CryptoNoContentValue()
        }
        val result = sender.act {
            component.findHSMKey(
                tenantId = knownTenantId,
                alias = knownAlias
            )
        }
        assertNull(result.value)
        val query = assertOperationType<HSMKeyInfoByAliasRpcQuery>(result)
        assertEquals(knownAlias, query.alias)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should find hsm details`() {
        val expectedValue = HSMInfo()
        setupCompletedResponse {
            expectedValue
        }
        val result = sender.act {
            component.findHSM(
                tenantId = knownTenantId,
                category = CryptoConsts.Categories.LEDGER
            )
        }
        assertNotNull(result.value)
        assertEquals(expectedValue, result.value)
        val query = assertOperationType<AssignedHSMRpcQuery>(result)
        assertEquals(CryptoConsts.Categories.LEDGER, query.category)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should return null for hsm details when it is not found`() {
        setupCompletedResponse {
            CryptoNoContentValue()
        }
        val result = sender.act {
            component.findHSM(
                tenantId = knownTenantId,
                category = CryptoConsts.Categories.LEDGER
            )
        }
        assertNull(result.value)
        val query = assertOperationType<AssignedHSMRpcQuery>(result)
        assertEquals(CryptoConsts.Categories.LEDGER, query.category)
        assertRequestContext(result)
    }

    @Test
    @Timeout(5)
    fun `Should cleanup created resources when component is stopped`() {
        component.stop()
        assertFalse(component.isRunning)
        assertNull(component.resources)
        verify(sender, atLeast(1)).stop()
    }

    @Test
    @Timeout(5)
    fun `Should fail when response tenant id does not match the request`() {
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, RpcOpsRequest::class.java)
            val future = CompletableFuture<RpcOpsResponse>()
            future.complete(
                RpcOpsResponse(
                    CryptoResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        UUID.randomUUID().toString(), //req.context.tenantId
                        req.context.other
                    ), CryptoNoContentValue()
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.findPublicKey(knownTenantId, knownAlias)
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(IllegalArgumentException::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should fail when requesting component in response does not match the request`() {
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, RpcOpsRequest::class.java)
            val future = CompletableFuture<RpcOpsResponse>()
            future.complete(
                RpcOpsResponse(
                    CryptoResponseContext(
                        UUID.randomUUID().toString(), //req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        req.context.tenantId,
                        req.context.other
                    ), CryptoNoContentValue()
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.findPublicKey(knownTenantId, knownAlias)
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(IllegalArgumentException::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should fail when response class is not expected`() {
        whenever(
            sender.sendRequest(any())
        ).then {
            val req = it.getArgument(0, RpcOpsRequest::class.java)
            val future = CompletableFuture<RpcOpsResponse>()
            future.complete(
                RpcOpsResponse(
                    CryptoResponseContext(
                        req.context.requestingComponent,
                        req.context.requestTimestamp,
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        req.context.tenantId,
                        req.context.other
                    ), CryptoPublicKeys(emptyList())
                )
            )
            future
        }
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.findPublicKey(knownTenantId, knownAlias)
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(IllegalArgumentException::class.java))
    }

    @Test
    @Timeout(5)
    fun `Should fail when sendRequest throws CryptoServiceLibraryException exception`() {
        val error = CryptoServiceLibraryException("Test failure.")
        whenever(sender.sendRequest(any())).thenThrow(error)
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.findPublicKey(knownTenantId, knownAlias)
        }
        assertSame(error, exception)
    }

    @Test
    @Timeout(5)
    fun `Should fail when sendRequest throws an exception`() {
        val error = RuntimeException("Test failure.")
        whenever(sender.sendRequest(any())).thenThrow(error)
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.findPublicKey(knownTenantId, knownAlias)
        }
        assertNotNull(exception.cause)
        assertSame(error, exception.cause)
    }
}