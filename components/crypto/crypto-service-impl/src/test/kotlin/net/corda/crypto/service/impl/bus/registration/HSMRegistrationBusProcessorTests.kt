package net.corda.crypto.service.impl.bus.registration

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.service.HSMService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.hsm.configuration.commands.PutHSMCommand
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.hsm.registration.commands.AssignHSMCommand
import net.corda.data.crypto.wire.hsm.registration.commands.AssignSoftHSMCommand
import net.corda.data.crypto.wire.hsm.registration.queries.AssignedHSMQuery
import net.corda.v5.base.util.toHex
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import net.corda.v5.crypto.sha256Bytes
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
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

class HSMRegistrationBusProcessorTests {
    companion object {
        private val tenantId = UUID.randomUUID().toString().toByteArray().sha256Bytes().toHex().take(12)

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
    fun `Should execute assign HSM request`() {
        val info = HSMInfo()
        val hsmService = mock<HSMService> {
            on { assignHSM(any(), any()) } doReturn info
        }
        val processor = HSMRegistrationBusProcessor(hsmService)
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignHSMCommand(CryptoConsts.HsmCategories.LEDGER)
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertSame(info, result.response)
        Mockito.verify(hsmService, times(1)).assignHSM(tenantId, CryptoConsts.HsmCategories.LEDGER)
    }

    @Test
    fun `Should execute assign Soft HSM request`() {
        val info = HSMInfo()
        val hsmService = mock<HSMService> {
            on { assignSoftHSM(any(), any(), any()) } doReturn info
        }
        val processor = HSMRegistrationBusProcessor(hsmService)
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignSoftHSMCommand(CryptoConsts.HsmCategories.LEDGER, "passphrase1")
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertSame(info, result.response)
        Mockito.verify(hsmService, times(1))
            .assignSoftHSM(tenantId, CryptoConsts.HsmCategories.LEDGER, "passphrase1")
    }

    @Test
    fun `Should execute find assigned HSM request`() {
        val info = HSMInfo()
        val hsmService = mock<HSMService> {
            on { findAssignedHSM(any(), any()) } doReturn info
        }
        val processor = HSMRegistrationBusProcessor(hsmService)
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignedHSMQuery(CryptoConsts.HsmCategories.LEDGER)
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertSame(info, result.response)
        Mockito.verify(hsmService, times(1)).findAssignedHSM(tenantId, CryptoConsts.HsmCategories.LEDGER)
    }

    @Test
    fun `Should return no content response when assigned HSM is not found`() {
        val hsmService = mock<HSMService>()
        val processor = HSMRegistrationBusProcessor(hsmService)
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignedHSMQuery(CryptoConsts.HsmCategories.LEDGER)
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response).isInstanceOf(CryptoNoContentValue::class.java)
        Mockito.verify(hsmService, times(1)).findAssignedHSM(tenantId, CryptoConsts.HsmCategories.LEDGER)
    }

    @Test
    fun `Should complete future exceptionally in case of unknown request`() {
        val hsmService = mock<HSMService>()
        val processor = HSMRegistrationBusProcessor(hsmService)
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                PutHSMCommand(HSMConfig())
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
            on { assignHSM(any(), any()) } doThrow  originalException
        }
        val processor = HSMRegistrationBusProcessor(hsmService)
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
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
        assertSame(originalException, exception.cause?.cause)
        Mockito.verify(hsmService, times(1)).assignHSM(tenantId, CryptoConsts.HsmCategories.LEDGER)
    }
}