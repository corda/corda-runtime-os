package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.config.impl.retrying
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoService
import net.corda.crypto.softhsm.TenantInfoService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.hsm.registration.commands.AssignSoftHSMCommand
import net.corda.data.crypto.wire.hsm.registration.queries.AssignedHSMQuery
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateWrappingKeyRpcCommand
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.EncodingUtils.toHex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
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
        private val tenantId = toHex(UUID.randomUUID().toString().toByteArray().sha256Bytes()).take(12)

        private val configEvent = ConfigChangedEvent(
            setOf(ConfigKeys.CRYPTO_CONFIG),
            mapOf(ConfigKeys.CRYPTO_CONFIG to SmartConfigFactory.createWithoutSecurityServices().create(
                createDefaultCryptoConfig("pass", "salt")
            )
            )
        )

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
            assertThat(actual.responseTimestamp.toEpochMilli())
                .isGreaterThanOrEqualTo(expected.requestTimestamp.toEpochMilli())
                .isLessThanOrEqualTo(now.toEpochMilli())
            assertTrue(
                actual.other.items.size == expected.other.items.size &&
                        actual.other.items.containsAll(expected.other.items) &&
                        expected.other.items.containsAll(actual.other.items)
            )
        }
    }

    @Test
    fun `Should execute handle AssignSoftHSMCommand`() {
        val info = HSMAssociationInfo()
        val tenantInfoService = mock<TenantInfoService> {
            on { populate(any(), any(), any()) } doReturn info
        }
        val cryptoService = mock<CryptoService> {}
        val processor = HSMRegistrationBusProcessor(tenantInfoService, cryptoService, configEvent.config.toCryptoConfig().retrying())
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignSoftHSMCommand(
                    CryptoConsts.Categories.LEDGER
                )
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response).isInstanceOf(HSMAssociationInfo::class.java)
        assertSame(info, result.response)
        Mockito.verify(tenantInfoService, times(1)).populate(
            eq(tenantId),
            eq(CryptoConsts.Categories.LEDGER),
            any()
        )
    }

    @Test
    fun `Should handle AssignedHSMQuery`() {
        val association = HSMAssociationInfo(
            UUID.randomUUID().toString(),
            tenantId,
            UUID.randomUUID().toString(),
            CryptoConsts.Categories.LEDGER,
            null,
            0
        )
        val tenantInfoService = mock<TenantInfoService> {
            on { lookup(any(), any()) } doReturn association
        }
        val cryptoService = mock<CryptoService>()
        val processor = HSMRegistrationBusProcessor(tenantInfoService, cryptoService, configEvent.config.toCryptoConfig().retrying())
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignedHSMQuery(CryptoConsts.Categories.LEDGER)
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response).isInstanceOf(HSMAssociationInfo::class.java)
        assertSame(association, result.response)
        Mockito.verify(tenantInfoService, times(1)).lookup(tenantId, CryptoConsts.Categories.LEDGER)
    }

    @Test
    fun `Should return no content response when handling AssignedMSMQQuery for unassigned category`() {
        val tenantInfoService = mock<TenantInfoService>()
        val cryptoService = mock<CryptoService>()
        val processor = HSMRegistrationBusProcessor(tenantInfoService, cryptoService, configEvent.config.toCryptoConfig().retrying())
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignedHSMQuery(CryptoConsts.Categories.LEDGER)
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response).isInstanceOf(CryptoNoContentValue::class.java)
        Mockito.verify(tenantInfoService, times(1)).lookup(tenantId, CryptoConsts.Categories.LEDGER)
    }

    @Test
    fun `Should complete future exceptionally with IllegalArgumentException in case of unknown request`() {
        val tenantInfoService = mock<TenantInfoService>()
        val cryptoService = mock<CryptoService>()
        val processor = HSMRegistrationBusProcessor(tenantInfoService, cryptoService , configEvent.config.toCryptoConfig().retrying())
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                GenerateWrappingKeyRpcCommand()
            ),
            future
        )
        val exception = assertThrows<ExecutionException> {
            future.get()
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Should complete future exceptionally in case of service failure`() {
        val originalException = RuntimeException()
        val tenantInfoService = mock<TenantInfoService> {
            on { populate(any(), any(), any()) } doThrow originalException
        }
        val cryptoService = mock<CryptoService>()
        val processor = HSMRegistrationBusProcessor(tenantInfoService, cryptoService, configEvent.config.toCryptoConfig().retrying())
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignSoftHSMCommand(
                    CryptoConsts.Categories.LEDGER
                )
            ),
            future
        )
        val exception = assertThrows<ExecutionException> {
            future.get()
        }
        assertNotNull(exception.cause)
        assertSame(originalException, exception.cause)
        Mockito.verify(tenantInfoService, times(1)).populate(
            eq(tenantId),
            eq(CryptoConsts.Categories.LEDGER),
            eq(cryptoService)
        )
    }
}