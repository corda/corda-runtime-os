package net.corda.crypto.client.impl

import net.corda.crypto.client.impl._utils.SendActResult
import net.corda.crypto.client.impl._utils.TestConfigurationReadService
import net.corda.crypto.client.impl._utils.act
import net.corda.crypto.client.impl._utils.generateKeyPair
import net.corda.crypto.client.impl._utils.signData
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureSchemes
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignWithSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.queries.AssignedHSMRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.ByIdsRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.FilterMyKeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.SupportedSchemesRpcQuery
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.test.util.eventually
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.core.IsInstanceOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertTrue

class CryptoOpsClientComponentTests {
    private lateinit var knownTenantId: String
    private lateinit var knownAlias: String
    private lateinit var knownOperationContext: Map<String, String>
    private lateinit var knownRawOperationContext: KeyValuePairList
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var sender: RPCSender<RpcOpsRequest, RpcOpsResponse>
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var component: CryptoOpsClientComponent

    @BeforeEach
    fun setup() {
            knownTenantId = UUID.randomUUID().toString()
            knownAlias = UUID.randomUUID().toString()
            knownOperationContext = mapOf(
                UUID.randomUUID().toString() to UUID.randomUUID().toString()
            )
            knownRawOperationContext = KeyValuePairList(
                knownOperationContext.map {
                    KeyValuePair(it.key, it.value)
                }
            )
            schemeMetadata = CipherSchemeMetadataImpl()
            sender = mock()
            coordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
            publisherFactory = mock {
                on { createRPCSender<RpcOpsRequest, RpcOpsResponse>(any(), any()) } doReturn sender
            }
            configurationReadService = TestConfigurationReadService(
                coordinatorFactory
            ).also {
                it.start()
                eventually {
                    assertTrue(it.isRunning)
                }
            }
            component = CryptoOpsClientComponent(
                coordinatorFactory = coordinatorFactory,
                publisherFactory = publisherFactory,
                schemeMetadata = schemeMetadata,
                configurationReadService = configurationReadService
            )
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
    fun `Should return supported scheme codes`() {
        setupCompletedResponse {
            CryptoSignatureSchemes(
                schemeMetadata.schemes.map { it.codeName }
            )
        }
        val result = sender.act {
            component.getSupportedSchemes(knownTenantId, CryptoConsts.HsmCategories.LEDGER)
        }
        assertNotNull(result.value)
        assertEquals(schemeMetadata.schemes.size, result.value!!.size)
        schemeMetadata.schemes.forEach {
            assertTrue(result.value.contains(it.codeName))
        }
        val query = assertOperationType<SupportedSchemesRpcQuery>(result)
        assertEquals(CryptoConsts.HsmCategories.LEDGER, query.category)
        assertRequestContext(result)
    }

    @Test
    fun `Should look up public key by its id`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        setupCompletedResponse {
            CryptoPublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val result = sender.act {
            component.lookup(knownTenantId, listOf(
                publicKeyIdOf(keyPair.public)
            ))
        }
        assertNotNull(result.value)
        assertEquals(1, result.value!!.size)
        assertArrayEquals(keyPair.public.encoded, result.value[0].publicKey.array())
        assertOperationType<ByIdsRpcQuery>(result)
        assertRequestContext(result)
    }

    @Test
    fun `Should return empty collection when public key id is not found`() {
        setupCompletedResponse {
            CryptoNoContentValue()
        }
        val result = sender.act {
            component.lookup(knownTenantId, listOf(publicKeyIdOf(UUID.randomUUID().toString().toByteArray())))
        }
        assertEquals(0, result.value!!.size)
        assertOperationType<ByIdsRpcQuery>(result)
        assertRequestContext(result)
    }

    @Test
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
    fun `Should filter my keys by proxy`() {
        val myPublicKeys = listOf(
            ByteBuffer.wrap(
                schemeMetadata.encodeAsByteArray(generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public)
            ),
            ByteBuffer.wrap(
                schemeMetadata.encodeAsByteArray(generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public)
            )
        )
        val notMyKey = ByteBuffer.wrap(
            schemeMetadata.encodeAsByteArray(generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public)
        )
        setupCompletedResponse {
            CryptoPublicKeys(myPublicKeys.map { it })
        }
        val result = sender.act {
            component.filterMyKeysProxy(knownTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        }
        assertNotNull(result.value)
        assertEquals(2, result.value!!.keys.size)
        assertTrue(result.value.keys.any { it == myPublicKeys[0] })
        assertTrue(result.value.keys.any { it == myPublicKeys[1] })
        val query = assertOperationType<FilterMyKeysRpcQuery>(result)
        assertEquals(3, query.keys.size)
        assertTrue(query.keys.any { it.array().contentEquals(myPublicKeys[0].array()) })
        assertTrue(query.keys.any { it.array().contentEquals(myPublicKeys[1].array()) })
        assertTrue(query.keys.any { it.array().contentEquals(notMyKey.array()) })
        assertRequestContext(result)
    }

    @Test
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
    fun `Should be able to handle empty filter my keys result by proxy`() {
        val myPublicKeys = listOf(
            ByteBuffer.wrap(
                schemeMetadata.encodeAsByteArray(generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public)
            ),
            ByteBuffer.wrap(
                schemeMetadata.encodeAsByteArray(generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public)
            )
        )
        val notMyKey = ByteBuffer.wrap(
            schemeMetadata.encodeAsByteArray(generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public)
        )
        setupCompletedResponse {
            CryptoPublicKeys(emptyList())
        }
        val result = sender.act {
            component.filterMyKeysProxy(knownTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        }
        assertNotNull(result.value)
        assertEquals(0, result.value!!.keys.size)
        val query = assertOperationType<FilterMyKeysRpcQuery>(result)
        assertEquals(3, query.keys.size)
        assertTrue(query.keys.any { it.array().contentEquals(myPublicKeys[0].array()) })
        assertTrue(query.keys.any { it.array().contentEquals(myPublicKeys[1].array()) })
        assertTrue(query.keys.any { it.array().contentEquals(notMyKey.array()) })
        assertRequestContext(result)
    }

    @Test
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
                category = CryptoConsts.HsmCategories.LEDGER,
                alias = knownAlias,
                context = knownOperationContext
            )
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value)
        val command = assertOperationType<GenerateKeyPairCommand>(result)
        assertEquals(CryptoConsts.HsmCategories.LEDGER, command.category)
        assertEquals(knownAlias, command.alias)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
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
    fun `Should generate fresh key without external id by proxy`() {
        val publicKey = ByteBuffer.wrap(
            schemeMetadata.encodeAsByteArray(generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public)
        )
        setupCompletedResponse {
            CryptoPublicKey(publicKey)
        }
        val result = sender.act {
            component.freshKeyProxy(knownTenantId, knownRawOperationContext)
        }
        assertNotNull(result.value)
        assertArrayEquals(result.value!!.key.array(), publicKey.array())
        val command = assertOperationType<GenerateFreshKeyRpcCommand>(result)
        assertNull(command.externalId)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
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
    fun `Should generate fresh key with external id by proxy`() {
        val externalId = UUID.randomUUID()
        val publicKey = ByteBuffer.wrap(
            schemeMetadata.encodeAsByteArray(generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public)
        )
        setupCompletedResponse {
            CryptoPublicKey(publicKey)
        }
        val result = sender.act {
            component.freshKeyProxy(knownTenantId, externalId, knownRawOperationContext)
        }
        assertNotNull(result.value)
        assertArrayEquals(result.value!!.key.array(), publicKey.array())
        val command = assertOperationType<GenerateFreshKeyRpcCommand>(result)
        assertNotNull(command.externalId)
        assertEquals(externalId, UUID.fromString(command.externalId))
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
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
    fun `Should sign by referencing public key by proxy`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val publicKey = ByteBuffer.wrap(
            schemeMetadata.encodeAsByteArray(keyPair.public)
        )
        val data = ByteBuffer.wrap(UUID.randomUUID().toString().toByteArray())
        val signature = signData(schemeMetadata, keyPair, data.array())
        setupCompletedResponse {
            CryptoSignatureWithKey(
                publicKey,
                ByteBuffer.wrap(signature)
            )
        }
        val result = sender.act {
            component.signProxy(knownTenantId, publicKey, data, knownRawOperationContext)
        }
        assertNotNull(result.value)
        assertArrayEquals(publicKey.array(), result.value!!.publicKey.array())
        assertArrayEquals(signature, result.value.bytes.array())
        val command = assertOperationType<SignRpcCommand>(result)
        assertNotNull(command)
        assertArrayEquals(publicKey.array(), command.publicKey.array())
        assertArrayEquals(data.array(), command.bytes.array())
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should sign by referencing public key and using custom signature spec`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = signData(schemeMetadata, keyPair, data)
        val spec = SignatureSpec(
            signatureName = "NONEwithECDSA",
            customDigestName = DigestAlgorithmName.SHA2_256
        )
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
        assertNull(command.signatureSpec.params)
        assertNotNull(command.signatureSpec.customDigestName)
        assertEquals(spec.customDigestName!!.name, command.signatureSpec.customDigestName)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should sign by referencing public key and using custom signature spec with signature params`() {
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = signData(schemeMetadata, keyPair, data)
        val spec = SignatureSpec(
            signatureName = "RSASSA-PSS",
            params = PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1
            )
        )
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
        assertNotNull(command.signatureSpec.params)
        assertEquals(PSSParameterSpec::class.java.name, command.signatureSpec.params.className)
        assertTrue(command.signatureSpec.params.bytes.array().isNotEmpty())
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should find hsm details`() {
        val expectedValue = HSMInfo()
        setupCompletedResponse {
            expectedValue
        }
        val result = sender.act {
            component.findHSM(
                tenantId = knownTenantId,
                category = CryptoConsts.HsmCategories.LEDGER
            )
        }
        assertNotNull(result.value)
        assertEquals(expectedValue, result.value)
        val query = assertOperationType<AssignedHSMRpcQuery>(result)
        assertEquals(CryptoConsts.HsmCategories.LEDGER, query.category)
        assertRequestContext(result)
    }

    @Test
    fun `Should return null for hsm details when it is not found`() {
        setupCompletedResponse {
            CryptoNoContentValue()
        }
        val result = sender.act {
            component.findHSM(
                tenantId = knownTenantId,
                category = CryptoConsts.HsmCategories.LEDGER
            )
        }
        assertNull(result.value)
        val query = assertOperationType<AssignedHSMRpcQuery>(result)
        assertEquals(CryptoConsts.HsmCategories.LEDGER, query.category)
        assertRequestContext(result)
    }

    @Test
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
            component.lookup(knownTenantId, emptyList())
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(IllegalArgumentException::class.java))
    }

    @Test
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
            component.lookup(knownTenantId, emptyList())
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(IllegalArgumentException::class.java))
    }

    @Test
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
            component.lookup(knownTenantId, emptyList())
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(IllegalArgumentException::class.java))
    }

    @Test
    fun `Should fail when sendRequest throws CryptoServiceLibraryException exception`() {
        val error = CryptoServiceLibraryException("Test failure.")
        whenever(sender.sendRequest(any())).thenThrow(error)
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.lookup(knownTenantId, emptyList())
        }
        assertSame(error, exception)
    }

    @Test
    fun `Should fail when sendRequest throws an exception`() {
        val error = RuntimeException("Test failure.")
        whenever(sender.sendRequest(any())).thenThrow(error)
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.lookup(knownTenantId, emptyList())
        }
        assertNotNull(exception.cause)
        assertSame(error, exception.cause)
    }

    @Test
    fun `Should cleanup created resources when component is stopped`() {
        kotlin.test.assertFalse(component.isRunning)
        Assertions.assertInstanceOf(CryptoOpsClientComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.ops
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            kotlin.test.assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        Assertions.assertInstanceOf(CryptoOpsClientComponent.ActiveImpl::class.java, component.impl)
        kotlin.test.assertNotNull(component.impl.ops)
        component.stop()
        eventually {
            kotlin.test.assertFalse(component.isRunning)
            kotlin.test.assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        Assertions.assertInstanceOf(CryptoOpsClientComponent.InactiveImpl::class.java, component.impl)
        Mockito.verify(sender, times(1)).close()
    }
}