package net.corda.crypto.client

import net.corda.crypto.CryptoConsts
import net.corda.crypto.testkit.CryptoMocks
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.ops.rpc.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.registration.hsm.HSMRegistrationRequest
import net.corda.libs.configuration.SmartConfigImpl.Companion.empty
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsInstanceOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

class CryptoOpsClientComponentTests : AbstractComponentTests<CryptoOpsClientComponentImpl>() {
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var sender: RPCSender<RpcOpsRequest, RpcOpsResponse>
    private lateinit var publisherFactory: PublisherFactory

    @BeforeEach
    fun setup() {
        super.setup {
            val cryptoMocks = CryptoMocks()
            schemeMetadata = cryptoMocks.schemeMetadata
            sender = mock()
            publisherFactory = mock {
                on { createRPCSender<RpcOpsRequest, RpcOpsResponse>(any(), any()) } doReturn sender
            }
            val instance = CryptoOpsClientComponentImpl(coordinatorFactory)
            instance.putPublisherFactory(publisherFactory)
            instance.putSchemeMetadataRef(schemeMetadata)
            instance
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

    @Test
    @Timeout(5)
    fun `Should generate key pair`() {
        val keyPair = generateKeyPair(schemeMetadata)
        setupCompletedResponse {
            CryptoPublicKey(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(keyPair.public))
            )
        }
        val result = sender.act {
            component.generateKeyPair(
                tenantId = "some-tenant",
                category = CryptoConsts.CryptoCategories.LEDGER,
                alias = "my-alias",
                context = mapOf(
                    "custom-key" to "custom-value"
                )
            )
        }
        assertNotNull(result)
        assertEquals(keyPair.public, result.value)

        assertThat(result.firstRequest, IsInstanceOf(GenerateFreshKeyRpcCommand::class.java))
        val command = result.firstRequest as GenerateFreshKeyRpcCommand
        assertSame(config, command.config)
        assertNotNull(command.context)
        assertNotNull(command.context.items)
        assertThat(command.context.items, empty())
        val context = result.firstRequest.context
        assertEquals(CryptoConsts.CLUSTER_TENANT_ID, context.tenantId)
        assertEquals(result.value.requestId, context.requestId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(HSMRegistrationPublisher::class.simpleName, context.requestingComponent)
        assertThat(context.other.items, empty())

        /*
        assertThat(result.requestId, not(emptyOrNullString()))
        val published = argumentCaptor<List<Record<*, *>>>()
        verify(publisher).publish(published.capture())
        assertEquals(1, published.allValues.size)
        assertEquals(1, published.firstValue.size)
        assertEquals(Schemas.Crypto.KEY_REGISTRATION_MESSAGE_TOPIC, published.firstValue[0].topic)
        assertEquals("some-tenant", published.firstValue[0].key)
        assertThat(published.firstValue[0].value, IsInstanceOf(KeyRegistrationRequest::class.java))
        val req = published.firstValue[0].value as KeyRegistrationRequest
        assertThat(req.request, IsInstanceOf(GenerateKeyPairCommand::class.java))
        val command = req.request as GenerateKeyPairCommand
        assertEquals(CryptoConsts.CryptoCategories.LEDGER, command.category)
        assertEquals("my-alias", command.alias)
        assertEquals(1, command.context.items.size)
        assertEquals("custom-key", command.context.items[0].key)
        assertEquals("custom-value", command.context.items[0].value)
        val context = req.context
        assertEquals("some-tenant", context.tenantId)
        assertEquals(result.requestId, context.requestId)
        assertThat(
            context.requestTimestamp.toEpochMilli(),
            allOf(
                greaterThanOrEqualTo(before.toEpochMilli()),
                lessThanOrEqualTo(after.toEpochMilli())
            )
        )
        assertEquals(KeyRegistrationPublisher::class.simpleName, context.requestingComponent)
        assertThat(context.other.items, not(empty()))
        assertTrue(context.other.items.any {
            it.key == CryptoConsts.Request.HSM_LABEL_CONTEXT_KEY
                    && it.value == "label:some-tenant:${CryptoConsts.CryptoCategories.LEDGER}"
        })

         */
    }
}