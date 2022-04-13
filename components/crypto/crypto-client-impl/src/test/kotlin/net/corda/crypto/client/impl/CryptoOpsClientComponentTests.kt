package net.corda.crypto.client.impl

import net.corda.crypto.client.impl._utils.SendActResult
import net.corda.crypto.client.impl._utils.TestConfigurationReadService
import net.corda.crypto.client.impl._utils.act
import net.corda.crypto.client.impl._utils.generateKeyPair
import net.corda.crypto.client.impl._utils.signData
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureSchemes
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignWithSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.queries.AssignedHSMRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.ByIdsRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.crypto.wire.ops.rpc.queries.KeysRpcQuery
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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFalse
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
            assertTrue(context.items.any { c -> it.key == c.key && it.value == c.value })
        }
    }

    private inline fun <reified OP> assertOperationType(result: SendActResult<RpcOpsRequest, *>): OP {
        assertNotNull(result.firstRequest.request)
        assertThat(result.firstRequest.request, IsInstanceOf(OP::class.java))
        return result.firstRequest.request as OP
    }

    @Test
    fun `Should return supported scheme codes`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
    fun `Should look up`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val now = Instant.now()
        setupCompletedResponse {
            CryptoSigningKeys(
                listOf(
                    CryptoSigningKey(
                        publicKeyIdOf(keyPair.public),
                        knownTenantId,
                        CryptoConsts.HsmCategories.LEDGER,
                        "alias1",
                        "hsmAlias1",
                        ByteBuffer.wrap(keyPair.public.encoded),
                        ECDSA_SECP256R1_CODE_NAME,
                        "master-key",
                        1,
                        "external-id",
                        now
                    )
                )
            )
        }
        val result = sender.act {
            component.lookup(
                tenantId = knownTenantId,
                skip = 20,
                take = 10,
                orderBy = CryptoKeyOrderBy.ALIAS_DESC,
                filter = mapOf(
                    CATEGORY_FILTER to CryptoConsts.HsmCategories.TLS,
                    ALIAS_FILTER to "alias1",
                    SCHEME_CODE_NAME_FILTER to ECDSA_SECP256R1_CODE_NAME,
                    MASTER_KEY_ALIAS_FILTER to "master-key",
                    CREATED_AFTER_FILTER to now.minusSeconds(100).toString(),
                    CREATED_BEFORE_FILTER to now.toString()
                )
            )
        }
        assertNotNull(result.value)
        assertEquals(1, result.value!!.size)
        assertEquals(publicKeyIdOf(keyPair.public), result.value[0].id)
        assertEquals(knownTenantId, result.value[0].tenantId)
        assertEquals(CryptoConsts.HsmCategories.LEDGER, result.value[0].category)
        assertEquals("alias1", result.value[0].alias)
        assertEquals("hsmAlias1", result.value[0].hsmAlias)
        assertArrayEquals(keyPair.public.encoded, result.value[0].publicKey.array())
        assertEquals(ECDSA_SECP256R1_CODE_NAME, result.value[0].schemeCodeName)
        assertEquals("master-key", result.value[0].masterKeyAlias)
        assertEquals(1, result.value[0].encodingVersion)
        assertEquals(now.epochSecond, result.value[0].created.epochSecond)
        val query = assertOperationType<KeysRpcQuery>(result)
        assertEquals(20, query.skip)
        assertEquals(10, query.take)
        assertEquals(CryptoKeyOrderBy.ALIAS_DESC, query.orderBy)
        assertEquals(CryptoConsts.HsmCategories.TLS, query.filter.items.first { it.key == CATEGORY_FILTER }.value)
        assertEquals(ECDSA_SECP256R1_CODE_NAME, query.filter.items.first { it.key == SCHEME_CODE_NAME_FILTER }.value)
        assertEquals("alias1", query.filter.items.first { it.key == ALIAS_FILTER }.value)
        assertEquals("master-key", query.filter.items.first { it.key == MASTER_KEY_ALIAS_FILTER }.value)
        assertEquals(
            now.minusSeconds(100).epochSecond,
            Instant.parse(query.filter.items.first { it.key == CREATED_AFTER_FILTER }.value).epochSecond
        )
        assertEquals(
            now.epochSecond,
            Instant.parse(query.filter.items.first { it.key == CREATED_BEFORE_FILTER }.value).epochSecond
        )
        assertRequestContext(result)
    }

    @Test
    fun `Should return empty collection when look up is not matching`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse {
            CryptoSigningKeys(emptyList())
        }
        val now = Instant.now()
        val result = sender.act {
            component.lookup(
                tenantId = knownTenantId,
                skip = 20,
                take = 10,
                orderBy = CryptoKeyOrderBy.ALIAS_DESC,
                filter = mapOf(
                    CATEGORY_FILTER to CryptoConsts.HsmCategories.TLS,
                    ALIAS_FILTER to "alias1",
                    SCHEME_CODE_NAME_FILTER to ECDSA_SECP256R1_CODE_NAME,
                    MASTER_KEY_ALIAS_FILTER to "master-key",
                    CREATED_AFTER_FILTER to now.minusSeconds(100).toString(),
                    CREATED_BEFORE_FILTER to now.toString()
                )
            )
        }
        assertEquals(0, result.value!!.size)
        val query = assertOperationType<KeysRpcQuery>(result)
        assertEquals(20, query.skip)
        assertEquals(10, query.take)
        assertEquals(CryptoKeyOrderBy.ALIAS_DESC, query.orderBy)
        assertEquals(CryptoConsts.HsmCategories.TLS, query.filter.items.first { it.key == CATEGORY_FILTER }.value)
        assertEquals(ECDSA_SECP256R1_CODE_NAME, query.filter.items.first { it.key == SCHEME_CODE_NAME_FILTER }.value)
        assertEquals("alias1", query.filter.items.first { it.key == ALIAS_FILTER }.value)
        assertEquals("master-key", query.filter.items.first { it.key == MASTER_KEY_ALIAS_FILTER }.value)
        assertEquals(
            now.minusSeconds(100).epochSecond,
            Instant.parse(query.filter.items.first { it.key == CREATED_AFTER_FILTER }.value).epochSecond
        )
        assertEquals(
            now.epochSecond,
            Instant.parse(query.filter.items.first { it.key == CREATED_BEFORE_FILTER }.value).epochSecond
        )
        assertRequestContext(result)
    }

    @Test
    fun `Should look up public key by its id`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val now = Instant.now()
        setupCompletedResponse {
            CryptoSigningKeys(
                listOf(
                    CryptoSigningKey(
                        publicKeyIdOf(keyPair.public),
                        knownTenantId,
                        CryptoConsts.HsmCategories.LEDGER,
                        "alias1",
                        "hsmAlias1",
                        ByteBuffer.wrap(keyPair.public.encoded),
                        ECDSA_SECP256R1_CODE_NAME,
                        "master-key",
                        1,
                        "external-id",
                        now
                    )
                )
            )
        }
        val result = sender.act {
            component.lookup(
                knownTenantId, listOf(
                    publicKeyIdOf(keyPair.public)
                )
            )
        }
        assertNotNull(result.value)
        assertEquals(1, result.value!!.size)
        assertEquals(publicKeyIdOf(keyPair.public), result.value[0].id)
        assertEquals(knownTenantId, result.value[0].tenantId)
        assertEquals(CryptoConsts.HsmCategories.LEDGER, result.value[0].category)
        assertEquals("alias1", result.value[0].alias)
        assertEquals("hsmAlias1", result.value[0].hsmAlias)
        assertArrayEquals(keyPair.public.encoded, result.value[0].publicKey.array())
        assertEquals(ECDSA_SECP256R1_CODE_NAME, result.value[0].schemeCodeName)
        assertEquals("master-key", result.value[0].masterKeyAlias)
        assertEquals(1, result.value[0].encodingVersion)
        assertEquals(now.epochSecond, result.value[0].created.epochSecond)
        val query = assertOperationType<ByIdsRpcQuery>(result)
        assertEquals(1, query.keys.size)
        assertEquals(publicKeyIdOf(keyPair.public), query.keys[0])
        assertRequestContext(result)
    }

    @Test
    fun `Should return empty collection when public key id is not found`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse {
            CryptoSigningKeys(emptyList())
        }
        val id = publicKeyIdOf(UUID.randomUUID().toString().toByteArray())
        val result = sender.act {
            component.lookup(knownTenantId, listOf(id))
        }
        assertEquals(0, result.value!!.size)
        val query = assertOperationType<ByIdsRpcQuery>(result)
        assertEquals(1, query.keys.size)
        assertEquals(id, query.keys[0])
        assertRequestContext(result)
    }

    @Test
    fun `Should filter my keys`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val myPublicKeys = listOf(
            generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public,
            generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public
        )
        val notMyKey = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public
        setupCompletedResponse {
            CryptoSigningKeys(
                myPublicKeys.map {
                    CryptoSigningKey(
                        publicKeyIdOf(it),
                        "tenant",
                        "LEDGER",
                        null,
                        null,
                        ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(it)),
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    )
                }
            )
        }
        val result = sender.act {
            component.filterMyKeys(knownTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        }
        assertNotNull(result.value)
        assertEquals(2, result.value!!.count())
        assertTrue(result.value.any { it == myPublicKeys[0] })
        assertTrue(result.value.any { it == myPublicKeys[1] })
        val query = assertOperationType<ByIdsRpcQuery>(result)
        assertEquals(3, query.keys.size)
        assertTrue(query.keys.any { it == publicKeyIdOf(schemeMetadata.encodeAsByteArray(myPublicKeys[0])) })
        assertTrue(query.keys.any { it == publicKeyIdOf(schemeMetadata.encodeAsByteArray(myPublicKeys[1])) })
        assertTrue(query.keys.any { it == publicKeyIdOf(schemeMetadata.encodeAsByteArray(notMyKey)) })
        assertRequestContext(result)
    }

    @Test
    fun `Should filter my keys by proxy`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
            CryptoSigningKeys(
                myPublicKeys.map {
                    CryptoSigningKey(
                        publicKeyIdOf(it.array()),
                        "tenant",
                        "LEDGER",
                        null,
                        null,
                        it,
                        "FAKE",
                        null,
                        null,
                        null,
                        Instant.now()
                    )                }
            )
        }
        val result = sender.act {
            component.filterMyKeysProxy(knownTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        }
        assertNotNull(result.value)
        assertEquals(2, result.value!!.keys.size)
        assertTrue(result.value.keys.any { it.publicKey.array().contentEquals(myPublicKeys[0].array()) })
        assertTrue(result.value.keys.any { it.publicKey.array().contentEquals(myPublicKeys[1].array()) })
        val query = assertOperationType<ByIdsRpcQuery>(result)
        assertEquals(3, query.keys.size)
        assertTrue(query.keys.any { it == publicKeyIdOf(myPublicKeys[0].array()) })
        assertTrue(query.keys.any { it == publicKeyIdOf(myPublicKeys[1].array()) })
        assertTrue(query.keys.any { it == publicKeyIdOf(notMyKey.array()) })
        assertRequestContext(result)
    }

    @Test
    fun `Should be able to handle empty filter my keys result`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val myPublicKeys = listOf(
            generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public,
            generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public
        )
        val notMyKey = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public
        setupCompletedResponse {
            CryptoSigningKeys(emptyList())
        }
        val result = sender.act {
            component.filterMyKeys(knownTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        }
        assertNotNull(result.value)
        assertEquals(0, result.value!!.count())
        val query = assertOperationType<ByIdsRpcQuery>(result)
        assertEquals(3, query.keys.size)
        assertTrue(query.keys.any { it == publicKeyIdOf(schemeMetadata.encodeAsByteArray(myPublicKeys[0])) })
        assertTrue(query.keys.any { it == publicKeyIdOf(schemeMetadata.encodeAsByteArray(myPublicKeys[1])) })
        assertTrue(query.keys.any { it == publicKeyIdOf(schemeMetadata.encodeAsByteArray(notMyKey)) })
        assertRequestContext(result)
    }

    @Test
    fun `Should be able to handle empty filter my keys result by proxy`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
            CryptoSigningKeys(emptyList())
        }
        val result = sender.act {
            component.filterMyKeysProxy(knownTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        }
        assertNotNull(result.value)
        assertEquals(0, result.value!!.keys.size)
        val query = assertOperationType<ByIdsRpcQuery>(result)
        assertEquals(3, query.keys.size)
        assertTrue(query.keys.any { it == publicKeyIdOf(myPublicKeys[0].array()) })
        assertTrue(query.keys.any { it == publicKeyIdOf(myPublicKeys[1].array()) })
        assertTrue(query.keys.any { it == publicKeyIdOf(notMyKey.array()) })
        assertRequestContext(result)
    }

    @Test
    fun `Should generate key pair without external id`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        assertNull(command.externalId)
        assertEquals(knownAlias, command.alias)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should generate key pair with external id`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        setupCompletedResponse {
            CryptoPublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val externalId = UUID.randomUUID().toString()
        val result = sender.act {
            component.generateKeyPair(
                tenantId = knownTenantId,
                category = CryptoConsts.HsmCategories.LEDGER,
                alias = knownAlias,
                externalId = externalId,
                context = knownOperationContext
            )
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value)
        val command = assertOperationType<GenerateKeyPairCommand>(result)
        assertEquals(CryptoConsts.HsmCategories.LEDGER, command.category)
        assertEquals(externalId, command.externalId)
        assertEquals(knownAlias, command.alias)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should generate fresh key without external id`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val externalId = UUID.randomUUID().toString()
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
        assertEquals(externalId, command.externalId)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should generate fresh key with external id by proxy`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
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
                    ), CryptoResponseContext()
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
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val error = CryptoServiceLibraryException("Test failure.")
        whenever(sender.sendRequest(any())).thenThrow(error)
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.lookup(knownTenantId, emptyList())
        }
        assertSame(error, exception)
    }

    @Test
    fun `Should fail when sendRequest throws an exception`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val error = RuntimeException("Test failure.")
        whenever(sender.sendRequest(any())).thenThrow(error)
        val exception = assertThrows<CryptoServiceLibraryException> {
            component.lookup(knownTenantId, emptyList())
        }
        assertNotNull(exception.cause)
        assertSame(error, exception.cause)
    }

    @Test
    fun `Should create active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoOpsClientComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.ops
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            kotlin.test.assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoOpsClientComponent.ActiveImpl::class.java, component.impl)
        kotlin.test.assertNotNull(component.impl.ops)
    }

    @Test
    fun `Should cleanup created resources when component is stopped`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoOpsClientComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.ops
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            kotlin.test.assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoOpsClientComponent.ActiveImpl::class.java, component.impl)
        kotlin.test.assertNotNull(component.impl.ops)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            kotlin.test.assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoOpsClientComponent.InactiveImpl::class.java, component.impl)
        Mockito.verify(sender, times(1)).stop()
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoOpsClientComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.ops
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoOpsClientComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.ops)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoOpsClientComponent.InactiveImpl::class.java, component.impl)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoOpsClientComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.ops)
        Mockito.verify(sender, atLeast(1)).stop()
    }
}