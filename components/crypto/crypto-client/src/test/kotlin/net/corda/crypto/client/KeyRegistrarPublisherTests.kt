package net.corda.crypto.client

import net.corda.crypto.CryptoConsts
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class KeyRegistrarPublisherTests {
    @Test
    fun `Should publish command to generate key pair`() {
        val published = argumentCaptor<List<Record<*, *>>>()
        val publisher = mock<Publisher> {
            on { publish(any()) } doReturn listOf(CompletableFuture<Unit>().also { it.complete(Unit) })
        }
        val cut = KeyRegistrarPublisher(publisher)
        val result = cut.generateKeyPair(
            tenantId = "some-tenant",
            category = CryptoConsts.CryptoCategories.LEDGER,
            alias = "my-alias",
            context = mapOf(
                "custom-key" to "custom-value"
            )
        )
        assertNotNull(result)
        verify(publisher).publish(published.capture())
        assertEquals(1, published.allValues.size)
    }
}