package net.corda.crypto.client

import net.corda.crypto.CryptoConsts
import net.corda.crypto.HSMLabelMap
import net.corda.data.crypto.wire.registration.key.GenerateKeyPairCommand
import net.corda.data.crypto.wire.registration.key.KeyRegistrationRequest
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.emptyOrNullString
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.hamcrest.Matchers.not
import org.hamcrest.core.IsInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class KeyRegistrationPublisherTests {

    private lateinit var publisher: Publisher
    private lateinit var labelMap: HSMLabelMap
    private lateinit var keyRegistrationPublisher: KeyRegistrationPublisher

    @BeforeEach
    fun setup() {
        publisher = mock {
            on { publish(any()) } doReturn listOf(CompletableFuture<Unit>().also { it.complete(Unit) })
        }
        labelMap = mock {
            on { get(any(), any()) } doAnswer {
                val tenantId = it.getArgument(0, String::class.java)
                val category = it.getArgument(1, String::class.java)
                "label:$tenantId:$category"
            }
        }
        keyRegistrationPublisher = KeyRegistrationPublisher(publisher, labelMap)
    }

    @Test
    @Timeout(5)
    fun `Should publish command to generate key pair`() {
        val before = Instant.now()
        val result = keyRegistrationPublisher.generateKeyPair(
            tenantId = "some-tenant",
            category = CryptoConsts.CryptoCategories.LEDGER,
            alias = "my-alias",
            context = mapOf(
                "custom-key" to "custom-value"
            )
        )
        val after = Instant.now()
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
    }
}