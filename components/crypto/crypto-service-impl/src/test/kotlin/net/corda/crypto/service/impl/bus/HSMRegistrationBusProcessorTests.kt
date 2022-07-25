package net.corda.crypto.service.impl.bus

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.HSMContext.NOT_FAIL_IF_ASSOCIATION_EXISTS
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY
import net.corda.crypto.core.CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_NONE
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.impl.config.createDefaultCryptoConfig
import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.service.HSMService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
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
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.toHex
import net.corda.v5.crypto.sha256Bytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.nio.ByteBuffer
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

        private val configEvent = ConfigChangedEvent(
            setOf(ConfigKeys.CRYPTO_CONFIG),
            mapOf(ConfigKeys.CRYPTO_CONFIG to createDefaultCryptoConfig(KeyCredentials("pass", "salt")))
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
            assertThat(actual.responseTimestamp.epochSecond)
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
    fun `Should handle AssignHSMCommand`() {
        val info = HSMInfo()
        val hsmService = mock<HSMService> {
            on { assignHSM(any(), any(), any()) } doReturn info
        }
        val processor = HSMRegistrationBusProcessor(hsmService, configEvent)
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignHSMCommand(
                    CryptoConsts.Categories.LEDGER,
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(PREFERRED_PRIVATE_KEY_POLICY_KEY, PREFERRED_PRIVATE_KEY_POLICY_NONE)
                        )
                    )
                )
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response).isInstanceOf(HSMInfo::class.java)
        assertSame(info, result.response)
        Mockito.verify(hsmService, times(1)).assignHSM(
            eq(tenantId),
            eq(CryptoConsts.Categories.LEDGER),
            argThat {
                this[PREFERRED_PRIVATE_KEY_POLICY_KEY] == PREFERRED_PRIVATE_KEY_POLICY_NONE
            }
        )
    }

    @Test
    fun `Should execute handle AssignSoftHSMCommand`() {
        val info = HSMInfo()
        val hsmService = mock<HSMService> {
            on { assignSoftHSM(any(), any(), any()) } doReturn info
        }
        val processor = HSMRegistrationBusProcessor(hsmService, configEvent)
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignSoftHSMCommand(
                    CryptoConsts.Categories.LEDGER,
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(NOT_FAIL_IF_ASSOCIATION_EXISTS, "YES")
                        )
                    )
                )
            ),
            future
        )
        val result = future.get()
        assertResponseContext(context, result.context)
        assertThat(result.response).isInstanceOf(HSMInfo::class.java)
        assertSame(info, result.response)
        Mockito.verify(hsmService, times(1)).assignSoftHSM(
            eq(tenantId),
            eq(CryptoConsts.Categories.LEDGER),
            argThat {
                this[NOT_FAIL_IF_ASSOCIATION_EXISTS] == "YES"
            }
        )
    }

    @Test
    fun `Should handle AssignedHSMQuery`() {
        val association =  HSMTenantAssociation(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = null,
            aliasSecret = null,
            config = HSMConfig(
                info = HSMInfo(),
                serviceConfig = "{}".toByteArray()
            ),
            deprecatedAt = 0
        )
        val hsmService = mock<HSMService> {
            on { findAssignedHSM(any(), any()) } doReturn association
        }
        val processor = HSMRegistrationBusProcessor(hsmService, configEvent)
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
        assertThat(result.response).isInstanceOf(HSMInfo::class.java)
        assertSame(association.config.info, result.response)
        Mockito.verify(hsmService, times(1)).findAssignedHSM(tenantId, CryptoConsts.Categories.LEDGER)
    }

    @Test
    fun `Should return no content response when handling AssignedMSMQQuery for unassigned category`() {
        val hsmService = mock<HSMService>()
        val processor = HSMRegistrationBusProcessor(hsmService, configEvent)
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
        Mockito.verify(hsmService, times(1)).findAssignedHSM(tenantId, CryptoConsts.Categories.LEDGER)
    }

    @Test
    fun `Should complete future exceptionally with IllegalArgumentException in case of unknown request`() {
        val hsmService = mock<HSMService>()
        val processor = HSMRegistrationBusProcessor(hsmService, configEvent)
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                PutHSMCommand(HSMInfo(), ByteBuffer.wrap("{}".toByteArray()))
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
        val hsmService = mock<HSMService> {
            on { assignHSM(any(), any(), any()) } doThrow originalException
        }
        val processor = HSMRegistrationBusProcessor(hsmService, configEvent)
        val context = createRequestContext()
        val future = CompletableFuture<HSMRegistrationResponse>()
        processor.onNext(
            HSMRegistrationRequest(
                context,
                AssignHSMCommand(
                    CryptoConsts.Categories.LEDGER,
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(PREFERRED_PRIVATE_KEY_POLICY_KEY, PREFERRED_PRIVATE_KEY_POLICY_NONE)
                        )
                    )
                )
            ),
            future
        )
        val exception = assertThrows<ExecutionException> {
            future.get()
        }
        assertNotNull(exception.cause)
        assertSame(originalException, exception.cause)
        Mockito.verify(hsmService, times(1)).assignHSM(
            eq(tenantId),
            eq(CryptoConsts.Categories.LEDGER),
            argThat {
                this[PREFERRED_PRIVATE_KEY_POLICY_KEY] == PREFERRED_PRIVATE_KEY_POLICY_NONE
            }
        )
    }
}