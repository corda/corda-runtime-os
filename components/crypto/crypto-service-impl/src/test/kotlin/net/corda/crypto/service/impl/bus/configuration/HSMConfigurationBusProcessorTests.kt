package net.corda.crypto.service.impl.bus.configuration

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.service.HSMService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationRequest
import net.corda.data.crypto.wire.hsm.configuration.HSMConfigurationResponse
import net.corda.data.crypto.wire.hsm.configuration.commands.PutHSMCommand
import net.corda.data.crypto.wire.hsm.registration.commands.AssignHSMCommand
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HSMConfigurationBusProcessorTests {
    companion object {
        private fun createRequestContext(): CryptoRequestContext = CryptoRequestContext(
            "test-component",
            Instant.now(),
            UUID.randomUUID().toString(),
            CryptoConsts.CLUSTER_TENANT_ID,
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
            Assertions.assertThat(actual.responseTimestamp.epochSecond)
                .isGreaterThanOrEqualTo(expected.requestTimestamp.epochSecond)
                .isLessThanOrEqualTo(now.epochSecond)
            assertTrue(
                actual.other.items.size == expected.other.items.size &&
                        actual.other.items.containsAll(expected.other.items) &&
                        expected.other.items.containsAll(actual.other.items)
            )
        }
    }

    @Test
    fun `Should execute put HSM config request`() {
        val hsmService = mock<HSMService>()
        val processor = HSMConfigurationBusProcessor(hsmService)
        val config = HSMConfig()
        val context = createRequestContext()
        val future = CompletableFuture<HSMConfigurationResponse>()
        processor.onNext(
            HSMConfigurationRequest(
                context,
                PutHSMCommand(config)
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertNotNull(result.response)
        assertThat(result.response).isInstanceOf(CryptoNoContentValue::class.java)
        Mockito.verify(hsmService, times(1)).putHSMConfig(config)
    }

    @Test
    fun `Should complete future exceptionally in case of unknown request`() {
        val hsmService = mock<HSMService>()
        val processor = HSMConfigurationBusProcessor(hsmService)
        val context = createRequestContext()
        val future = CompletableFuture<HSMConfigurationResponse>()
        processor.onNext(
            HSMConfigurationRequest(
                context,
                AssignHSMCommand(CryptoConsts.HsmCategories.LEDGER)
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
    fun `Should complete future exceptionally in case of service failure`() {
        val originalException = RuntimeException()
        val hsmService = mock<HSMService> {
            on { putHSMConfig(any()) } doThrow  originalException
        }
        val processor = HSMConfigurationBusProcessor(hsmService)
        val config = HSMConfig()
        val context = createRequestContext()
        val future = CompletableFuture<HSMConfigurationResponse>()
        processor.onNext(
            HSMConfigurationRequest(
                context,
                PutHSMCommand(config)
            ),
            future
        )
        val exception = assertThrows<ExecutionException> {
            future.get()
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause).isInstanceOf(CryptoServiceLibraryException::class.java)
        assertSame(originalException, exception.cause?.cause)
        Mockito.verify(hsmService, times(1)).putHSMConfig(config)
    }
}