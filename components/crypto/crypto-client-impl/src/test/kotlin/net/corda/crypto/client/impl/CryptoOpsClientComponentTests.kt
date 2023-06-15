package net.corda.crypto.client.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.component.impl.exceptionFactories
import net.corda.crypto.component.test.utils.SendActResult
import net.corda.crypto.component.test.utils.TestConfigurationReadService
import net.corda.crypto.component.test.utils.TestRPCSender
import net.corda.crypto.component.test.utils.act
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.component.test.utils.reportDownComponents
import net.corda.crypto.component.test.utils.signData
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.ShortHashes
import net.corda.data.crypto.wire.CryptoDerivedSharedSecret
import net.corda.data.crypto.wire.CryptoKeySchemes
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
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
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.test.util.eventually
import net.corda.v5.base.util.EncodingUtils.toHex
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.exceptions.CryptoException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class TestExecutionContext(val schemeMetadata: CipherSchemeMetadata) {
    val coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
    val configurationReadService: TestConfigurationReadService = TestConfigurationReadService(
        coordinatorFactory
    ).also {
        it.start()
        eventually {
            assertTrue(it.isRunning)
        }
    }
    val sender = TestRPCSender<RpcOpsRequest, RpcOpsResponse>(coordinatorFactory)
    val publisherFactory = mock<PublisherFactory> {
        on { createRPCSender<RpcOpsRequest, RpcOpsResponse>(any(), any()) } doReturn sender
    }
    val component = CryptoOpsClientComponent(
        coordinatorFactory = coordinatorFactory,
        publisherFactory = publisherFactory,
        schemeMetadata = schemeMetadata,
        configurationReadService = configurationReadService,
        digestService = mock(),
        0
    )
}

class CryptoOpsClientComponentTests {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @JvmStatic
        fun knownCordaRPCAPIResponderExceptions(): List<Class<*>> =
            exceptionFactories.keys.map { Class.forName(it) }

        val schemeMetadata = CipherSchemeMetadataImpl()
        val knownTenantId = toHex(UUID.randomUUID().toString().toByteArray().sha256Bytes()).take(12)
        val knownAlias = UUID.randomUUID().toString()
        val knownOperationContext = mapOf(
            UUID.randomUUID().toString() to UUID.randomUUID().toString()
        )
        val knownRawOperationContext = KeyValuePairList(
            knownOperationContext.map {
                KeyValuePair(it.key, it.value)
            }
        )

        // most tests can share an execution context, so make one at file level to use in most cases
        val context = TestExecutionContext(schemeMetadata)
        val coordinatorFactory = context.coordinatorFactory
        val sender = context.sender
        val component = context.component
    }

    private fun setupCompletedResponse(respFactory: (RpcOpsRequest) -> Any) {
        sender.setupCompletedResponse { req ->
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
        }
    }

    private fun assertRequestContext(result: SendActResult<*>, tenantId: String = knownTenantId) {
        assertNotNull(sender.lastRequest)
        val context = sender.lastRequest!!.context
        kotlin.test.assertEquals(tenantId, context.tenantId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(CryptoOpsClientImpl::class.simpleName, context.requestingComponent)
        assertThat(context.other.items).isEmpty()
    }

    private fun assertOperationContext(context: KeyValuePairList) {
        assertNotNull(context.items)
        assertEquals(1, context.items.size)
        knownOperationContext.forEach {
            assertTrue(context.items.any { c -> it.key == c.key && it.value == c.value })
        }
    }

    private inline fun <reified OP> assertOperationType(): OP {
        assertNotNull(sender.lastRequest)
        assertNotNull(sender.lastRequest!!.request)
        assertThat(sender.lastRequest!!.request).isInstanceOf(OP::class.java)
        return sender.lastRequest!!.request as OP
    }

    @Test
    fun `Should return supported scheme codes`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse {
            CryptoKeySchemes(
                schemeMetadata.schemes.map { it.codeName }
            )
        }
        val result = sender.act {
            component.getSupportedSchemes(knownTenantId, CryptoConsts.Categories.LEDGER)
        }
        assertNotNull(result.value)
        assertEquals(schemeMetadata.schemes.size, result.value.size)
        schemeMetadata.schemes.forEach {
            assertTrue(result.value.contains(it.codeName))
        }
        val query = assertOperationType<SupportedSchemesRpcQuery>()
        assertEquals(CryptoConsts.Categories.LEDGER, query.category)
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
                        keyPair.public.publicKeyId(),
                        knownTenantId,
                        CryptoConsts.Categories.LEDGER,
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
                    CATEGORY_FILTER to CryptoConsts.Categories.TLS,
                    ALIAS_FILTER to "alias1",
                    SCHEME_CODE_NAME_FILTER to ECDSA_SECP256R1_CODE_NAME,
                    MASTER_KEY_ALIAS_FILTER to "master-key",
                    CREATED_AFTER_FILTER to now.minusSeconds(100).toString(),
                    CREATED_BEFORE_FILTER to now.toString()
                )
            )
        }
        assertNotNull(result.value)
        assertEquals(1, result.value.size)
        assertEquals(keyPair.public.publicKeyId(), result.value[0].id)
        assertEquals(knownTenantId, result.value[0].tenantId)
        assertEquals(CryptoConsts.Categories.LEDGER, result.value[0].category)
        assertEquals("alias1", result.value[0].alias)
        assertEquals("hsmAlias1", result.value[0].hsmAlias)
        assertArrayEquals(keyPair.public.encoded, result.value[0].publicKey.array())
        assertEquals(ECDSA_SECP256R1_CODE_NAME, result.value[0].schemeCodeName)
        assertEquals("master-key", result.value[0].masterKeyAlias)
        assertEquals(1, result.value[0].encodingVersion)
        assertEquals(now.epochSecond, result.value[0].created.epochSecond)
        val query = assertOperationType<KeysRpcQuery>()
        assertEquals(20, query.skip)
        assertEquals(10, query.take)
        assertEquals(CryptoKeyOrderBy.ALIAS_DESC, query.orderBy)
        assertEquals(CryptoConsts.Categories.TLS, query.filter.items.first { it.key == CATEGORY_FILTER }.value)
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
                    CATEGORY_FILTER to CryptoConsts.Categories.TLS,
                    ALIAS_FILTER to "alias1",
                    SCHEME_CODE_NAME_FILTER to ECDSA_SECP256R1_CODE_NAME,
                    MASTER_KEY_ALIAS_FILTER to "master-key",
                    CREATED_AFTER_FILTER to now.minusSeconds(100).toString(),
                    CREATED_BEFORE_FILTER to now.toString()
                )
            )
        }
        assertEquals(0, result.value.size)
        val query = assertOperationType<KeysRpcQuery>()
        assertEquals(20, query.skip)
        assertEquals(10, query.take)
        assertEquals(CryptoKeyOrderBy.ALIAS_DESC, query.orderBy)
        assertEquals(CryptoConsts.Categories.TLS, query.filter.items.first { it.key == CATEGORY_FILTER }.value)
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
                        keyPair.public.publicKeyId(),
                        knownTenantId,
                        CryptoConsts.Categories.LEDGER,
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
            component.lookupKeysByIds(
                knownTenantId,
                listOf(
                    ShortHash.of(keyPair.public.publicKeyId())
                )
            )
        }
        assertNotNull(result.value)
        assertEquals(1, result.value.size)
        assertEquals(keyPair.public.publicKeyId(), result.value[0].id)
        assertEquals(knownTenantId, result.value[0].tenantId)
        assertEquals(CryptoConsts.Categories.LEDGER, result.value[0].category)
        assertEquals("alias1", result.value[0].alias)
        assertEquals("hsmAlias1", result.value[0].hsmAlias)
        assertArrayEquals(keyPair.public.encoded, result.value[0].publicKey.array())
        assertEquals(ECDSA_SECP256R1_CODE_NAME, result.value[0].schemeCodeName)
        assertEquals("master-key", result.value[0].masterKeyAlias)
        assertEquals(1, result.value[0].encodingVersion)
        assertEquals(now.epochSecond, result.value[0].created.epochSecond)
        val query = assertOperationType<ByIdsRpcQuery>()
        assertEquals(1, (query.keyIds as ShortHashes).hashes.size)
        assertEquals(keyPair.public.publicKeyId(), (query.keyIds as ShortHashes).hashes[0])
        assertRequestContext(result)
    }

    @Test
    fun `lookup should throw IllegalArgumentException when number of ids exceeds the limit`() {
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
                        keyPair.public.publicKeyId(),
                        knownTenantId,
                        CryptoConsts.Categories.LEDGER,
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
        val ids = (0..KEY_LOOKUP_INPUT_ITEMS_LIMIT).map {
            ShortHash.of(keyPair.public.publicKeyId())
        }
        assertThrows(IllegalArgumentException::class.java) {
            component.lookupKeysByIds(knownTenantId, ids)
        }
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
        val id = publicKeyIdFromBytes(UUID.randomUUID().toString().toByteArray())
        val result = sender.act {
            component.lookupKeysByIds(knownTenantId, listOf(ShortHash.of(id)))
        }
        assertEquals(0, result.value.size)
        val query = assertOperationType<ByIdsRpcQuery>()
        assertEquals(1, (query.keyIds as ShortHashes).hashes.size)
        assertEquals(id, (query.keyIds as ShortHashes).hashes[0])
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
                        it.publicKeyId(),
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
        assertEquals(2, result.value.count())
        assertTrue(result.value.any { it == myPublicKeys[0] })
        assertTrue(result.value.any { it == myPublicKeys[1] })
        val query = assertOperationType<ByIdsRpcQuery>()
        val keyIds = (query.keyIds as ShortHashes).hashes
        assertEquals(3, keyIds.size)
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(schemeMetadata.encodeAsByteArray(myPublicKeys[0])) })
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(schemeMetadata.encodeAsByteArray(myPublicKeys[1])) })
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(schemeMetadata.encodeAsByteArray(notMyKey)) })
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
                        publicKeyIdFromBytes(it.array()),
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
                    )
                }
            )
        }
        val result = sender.act {
            component.filterMyKeysProxy(knownTenantId, listOf(myPublicKeys[0], myPublicKeys[1], notMyKey))
        }
        assertNotNull(result.value)
        assertEquals(2, result.value.keys.size)
        assertTrue(result.value.keys.any { it.publicKey.array().contentEquals(myPublicKeys[0].array()) })
        assertTrue(result.value.keys.any { it.publicKey.array().contentEquals(myPublicKeys[1].array()) })
        val query = assertOperationType<ByIdsRpcQuery>()
        val keyIds = (query.keyIds as ShortHashes).hashes
        assertEquals(3, keyIds.size)
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(myPublicKeys[0].array()) })
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(myPublicKeys[1].array()) })
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(notMyKey.array()) })
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
        assertEquals(0, result.value.count())
        val query = assertOperationType<ByIdsRpcQuery>()
        val keyIds = (query.keyIds as ShortHashes).hashes
        assertEquals(3, keyIds.size)
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(schemeMetadata.encodeAsByteArray(myPublicKeys[0])) })
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(schemeMetadata.encodeAsByteArray(myPublicKeys[1])) })
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(schemeMetadata.encodeAsByteArray(notMyKey)) })
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
        assertEquals(0, result.value.keys.size)
        val query = assertOperationType<ByIdsRpcQuery>()
        val keyIds = (query.keyIds as ShortHashes).hashes
        assertEquals(3, keyIds.size)
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(myPublicKeys[0].array()) })
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(myPublicKeys[1].array()) })
        assertTrue(keyIds.any { it == publicKeyIdFromBytes(notMyKey.array()) })
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
                category = CryptoConsts.Categories.LEDGER,
                alias = knownAlias,
                scheme = ECDSA_SECP256R1_CODE_NAME,
                context = knownOperationContext
            )
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value)
        val command = assertOperationType<GenerateKeyPairCommand>()
        assertEquals(CryptoConsts.Categories.LEDGER, command.category)
        assertNull(command.externalId)
        assertEquals(knownAlias, command.alias)
        assertEquals(ECDSA_SECP256R1_CODE_NAME, command.schemeCodeName)
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
                category = CryptoConsts.Categories.LEDGER,
                alias = knownAlias,
                externalId = externalId,
                scheme = ECDSA_SECP256R1_CODE_NAME,
                context = knownOperationContext
            )
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value)
        val command = assertOperationType<GenerateKeyPairCommand>()
        assertEquals(CryptoConsts.Categories.LEDGER, command.category)
        assertEquals(externalId, command.externalId)
        assertEquals(knownAlias, command.alias)
        assertEquals(ECDSA_SECP256R1_CODE_NAME, command.schemeCodeName)
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
            component.freshKey(
                knownTenantId,
                CryptoConsts.Categories.CI,
                ECDSA_SECP256R1_CODE_NAME,
                knownOperationContext
            )
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value)
        val command = assertOperationType<GenerateFreshKeyRpcCommand>()
        assertNull(command.externalId)
        assertEquals(CryptoConsts.Categories.CI, command.category)
        assertEquals(ECDSA_SECP256R1_CODE_NAME, command.schemeCodeName)
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
            component.freshKey(
                knownTenantId,
                CryptoConsts.Categories.CI,
                externalId,
                ECDSA_SECP256R1_CODE_NAME,
                knownOperationContext
            )
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value)
        val command = assertOperationType<GenerateFreshKeyRpcCommand>()
        assertNotNull(command.externalId)
        assertEquals(CryptoConsts.Categories.CI, command.category)
        assertEquals(externalId, command.externalId)
        assertEquals(ECDSA_SECP256R1_CODE_NAME, command.schemeCodeName)
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should generate wrapping key`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val hsmId = UUID.randomUUID().toString()
        val masterKeyAlias = UUID.randomUUID().toString()
        setupCompletedResponse {
            CryptoNoContentValue()
        }
        val result = sender.act {
            component.createWrappingKey(
                hsmId = hsmId,
                failIfExists = true,
                masterKeyAlias = masterKeyAlias,
                context = knownOperationContext
            )
        }
        assertNotNull(result.value)
        val command = assertOperationType<GenerateWrappingKeyRpcCommand>()
        assertEquals(hsmId, command.hsmId)
        assertEquals(masterKeyAlias, command.masterKeyAlias)
        assertTrue(command.failIfExists)
        assertOperationContext(command.context)
        assertRequestContext(result, CryptoTenants.CRYPTO)
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
        val signature = signData(schemeMetadata, SignatureSpecs.ECDSA_SHA256, keyPair, data.array())
        val spec = CryptoSignatureSpec(
            "NONEwithECDSA",
            DigestAlgorithmName.SHA2_256.name,
            null
        )
        setupCompletedResponse {
            CryptoSignatureWithKey(
                publicKey,
                ByteBuffer.wrap(signature)
            )
        }
        val result = sender.act {
            component.signProxy(knownTenantId, publicKey, spec, data, knownRawOperationContext)
        }
        assertNotNull(result.value)
        assertArrayEquals(publicKey.array(), result.value.publicKey.array())
        assertArrayEquals(signature, result.value.bytes.array())
        val command = assertOperationType<SignRpcCommand>()
        assertNotNull(command)
        assertSame(spec, command.signatureSpec)
        assertArrayEquals(publicKey.array(), command.publicKey.array())
        assertArrayEquals(data.array(), command.bytes.array())
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should throw IllegalArgumentException when signature spec cannot be inferred for the digest`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        assertThrows(IllegalArgumentException::class.java) {
            component.sign(
                knownTenantId,
                keyPair.public,
                DigestAlgorithmName("--NONSENSE--"),
                UUID.randomUUID().toString().toByteArray()
            )
        }
    }

    @Test
    fun `Should sign inferring signature spec from digest`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = signData(schemeMetadata, SignatureSpecs.ECDSA_SHA256, keyPair, data)
        setupCompletedResponse {
            CryptoSignatureWithKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public)),
                ByteBuffer.wrap(signature)
            )
        }
        val result = sender.act {
            component.sign(knownTenantId, keyPair.public, DigestAlgorithmName.SHA2_256, data, knownOperationContext)
        }
        assertNotNull(result.value)
        assertEquals(keyPair.public, result.value.by)
        assertArrayEquals(signature, result.value.bytes)
        val command = assertOperationType<SignRpcCommand>()
        assertNotNull(command)
        assertEquals(SignatureSpecs.ECDSA_SHA256.signatureName, command.signatureSpec.signatureName)
        assertNull(command.signatureSpec.customDigestName)
        assertNull(command.signatureSpec.params)
        assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPair.public), command.publicKey.array())
        assertArrayEquals(data, command.bytes.array())
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should sign explicitly using signature spec`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val data = UUID.randomUUID().toString().toByteArray()
        val signature = signData(schemeMetadata, SignatureSpecs.ECDSA_SHA256, keyPair, data)
        val spec = CustomSignatureSpec(
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
        assertEquals(keyPair.public, result.value.by)
        assertArrayEquals(signature, result.value.bytes)
        val command = assertOperationType<SignRpcCommand>()
        assertNotNull(command)
        assertEquals(spec.signatureName, command.signatureSpec.signatureName)
        assertNotNull(command.signatureSpec.customDigestName)
        assertEquals(spec.customDigestName.name, command.signatureSpec.customDigestName)
        assertNull(command.signatureSpec.params)
        assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPair.public), command.publicKey.array())
        assertArrayEquals(data, command.bytes.array())
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should derive shared secret`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val keyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val otherKeyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val secret = UUID.randomUUID().toString().toByteArray()
        setupCompletedResponse {
            CryptoDerivedSharedSecret(ByteBuffer.wrap(secret))
        }
        val result = sender.act {
            component.deriveSharedSecret(knownTenantId, keyPair.public, otherKeyPair.public, knownOperationContext)
        }
        assertNotNull(result.value)
        assertArrayEquals(secret, result.value)
        val command = assertOperationType<DeriveSharedSecretCommand>()
        assertNotNull(command)
        assertArrayEquals(schemeMetadata.encodeAsByteArray(keyPair.public), command.publicKey.array())
        assertArrayEquals(schemeMetadata.encodeAsByteArray(otherKeyPair.public), command.otherPublicKey.array())
        assertOperationContext(command.context)
        assertRequestContext(result)
    }

    @Test
    fun `Should throw IllegalStateException when response tenant id does not match the request`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse { req ->
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
        }
        assertThrows(IllegalStateException::class.java) {
            component.lookupKeysByIds(knownTenantId, emptyList())
        }
    }

    @Test
    fun `Should throw IllegalStateException when requesting component in response does not match the request`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse { req ->
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
        }
        assertThrows(IllegalStateException::class.java) {
            component.lookupKeysByIds(knownTenantId, emptyList())
        }
    }

    @Test
    fun `Should throw IllegalStateException when response class is not expected`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        setupCompletedResponse { req ->
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
        }
        assertThrows(IllegalStateException::class.java) {
            component.lookupKeysByIds(knownTenantId, emptyList())
        }
    }

    @ParameterizedTest
    @MethodSource("knownCordaRPCAPIResponderExceptions")
    @Suppress("MaxLineLength")
    fun `Should throw exception wrapped in CordaRPCAPIResponderException when sendRequest throws it as errorType`(
        expected: Class<out Throwable>
    ) {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val error = CordaRPCAPIResponderException(
            errorType = expected.name,
            message = "Test failure."
        )
        setupCompletedResponse { throw error }
        val exception = assertThrows(expected) {
            component.lookupKeysByIds(knownTenantId, emptyList())
        }
        assertEquals(error.message, exception.message)
    }

    @Test
    fun `Should throw CryptoException when sendRequest fails`() {
        component.start()
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val error = CordaRPCAPIResponderException(
            errorType = RuntimeException::class.java.name,
            message = "Test failure."
        )
        setupCompletedResponse { throw error }
        val exception = assertThrows(CryptoException::class.java) {
            component.lookupKeysByIds(knownTenantId, emptyList())
        }
        assertSame(error, exception.cause)
    }

    @Test
    fun `Should create active implementation only after the component is UP`() {
        assertFalse(component.isRunning)
        assertThrows(IllegalStateException::class.java) {
            component.impl.ops
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(
                LifecycleStatus.UP,
                component.lifecycleCoordinator.status,
                coordinatorFactory.reportDownComponents(logger)
            )
        }
        assertNotNull(component.impl.ops)
    }

    @Test
    fun `Should cleanup created resources when component is stopped`() {
        val my = TestExecutionContext(schemeMetadata)
        assertFalse(my.component.isRunning)
        assertThrows(IllegalStateException::class.java) {
            my.component.impl.ops
        }
        my.component.start()
        eventually {
            assertTrue(my.component.isRunning)
            assertEquals(LifecycleStatus.UP, my.component.lifecycleCoordinator.status)
        }
        kotlin.test.assertNotNull(my.component.impl.ops)
        my.component.stop()
        eventually {
            assertFalse(my.component.isRunning)
            assertEquals(LifecycleStatus.DOWN, my.component.lifecycleCoordinator.status)
        }
        assertEquals(1, my.sender.stopped.get())
    }

    @Test
    fun `Should go UP and DOWN as its config reader goes UP and DOWN`() {
        val my = TestExecutionContext(schemeMetadata)
        assertFalse(my.component.isRunning)
        assertThrows(IllegalStateException::class.java) {
            my.component.impl.ops
        }
        my.component.start()
        eventually {
            assertTrue(my.component.isRunning)
            assertEquals(LifecycleStatus.UP, my.component.lifecycleCoordinator.status)
        }
        assertNotNull(my.component.impl.ops)
        my.configurationReadService.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, my.component.lifecycleCoordinator.status)
        }
        my.configurationReadService.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, my.component.lifecycleCoordinator.status)
        }
        assertNotNull(my.component.impl.ops)
        assertThat(my.sender.stopped.get()).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `Should go UP and DOWN as its downstream dependencies go UP and DOWN`() {
        val my = TestExecutionContext(schemeMetadata)
        assertFalse(my.component.isRunning)
        assertThrows(IllegalStateException::class.java) {
            my.component.impl.ops
        }
        my.component.start()
        eventually {
            assertTrue(my.component.isRunning)
            assertEquals(LifecycleStatus.UP, my.component.lifecycleCoordinator.status)
        }
        assertNotNull(my.component.impl.ops)
        my.sender.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, my.component.lifecycleCoordinator.status)
        }
        my.sender.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, my.component.lifecycleCoordinator.status)
        }
        assertNotNull(my.component.impl.ops)
        assertEquals(0, my.sender.stopped.get())
    }

    @Test
    fun `Should recreate active implementation on config change`() {
        val my = TestExecutionContext(schemeMetadata)
        assertFalse(my.component.isRunning)
        assertThrows(IllegalStateException::class.java) {
            my.component.impl.ops
        }
        my.component.start()
        eventually {
            assertTrue(my.component.isRunning)
            assertEquals(LifecycleStatus.UP, my.component.lifecycleCoordinator.status)
        }
        val originalImpl = my.component.impl
        assertNotNull(my.component.impl.ops)
        my.configurationReadService.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, my.component.lifecycleCoordinator.status)
        }
        my.configurationReadService.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, my.component.lifecycleCoordinator.status)
        }
        logger.info("REISSUING ConfigChangedEvent")
        my.configurationReadService.reissueConfigChangedEvent(my.component.lifecycleCoordinator)
        eventually {
            assertNotSame(originalImpl, my.component.impl)
        }
        assertThat(my.sender.stopped.get()).isGreaterThanOrEqualTo(1)
    }
}
