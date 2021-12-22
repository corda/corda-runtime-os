package net.corda.crypto.client

import net.corda.crypto.CryptoConsts
import net.corda.data.crypto.config.HSMConfig
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.registration.hsm.AddHSMCommand
import net.corda.data.crypto.wire.registration.hsm.AssignHSMCommand
import net.corda.data.crypto.wire.registration.hsm.AssignSoftHSMCommand
import net.corda.data.crypto.wire.registration.hsm.HSMRegistrationRequest
import net.corda.messaging.api.publisher.Publisher
import net.corda.schema.Schemas
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.emptyOrNullString
import org.hamcrest.Matchers.not
import org.hamcrest.core.IsInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class HSMRegistrationPublisherTests {

    private lateinit var publisher: Publisher
    private lateinit var registrar: HSMRegistrationPublisher

    @BeforeEach
    fun setup() {
        publisher = mock {
            on { publish(any()) } doReturn listOf(CompletableFuture<Unit>().also { it.complete(Unit) })
        }
        registrar = HSMRegistrationPublisher(publisher)
    }

    @Test
    @Timeout(5)
    fun `Should publish command to add HSM configuration`() {
        val config = HSMConfig(
            HSMInfo(
                UUID.randomUUID().toString(),
                Instant.now(),
                7,
                "default",
                "Test HSM",
                "default",
                null,
                listOf(CryptoConsts.CryptoCategories.LEDGER, CryptoConsts.CryptoCategories.TLS),
                11,
                5500,
                listOf(ECDSA_SECP256K1_CODE_NAME, ECDSA_SECP256R1_CODE_NAME, EDDSA_ED25519_CODE_NAME),
                listOf(ECDSA_SECP256K1_CODE_NAME, ECDSA_SECP256R1_CODE_NAME, EDDSA_ED25519_CODE_NAME)
            ),
            ByteBuffer.allocate(0)
        )
        val result = publisher.act {
            registrar.putHSM(config)
        }
        assertThat(result.value.requestId, not(emptyOrNullString()))
        assertEquals(1, result.messages.size)
        assertEquals(1, result.messages.first().size)
        assertEquals(Schemas.Crypto.HSM_REGISTRATION_MESSAGE_TOPIC, result.firstRecord.topic)
        assertEquals(CryptoConsts.CLUSTER_TENANT_ID, result.firstRecord.key)
        assertThat(result.firstRecord.value, IsInstanceOf(HSMRegistrationRequest::class.java))
        val req = result.firstRecord.value as HSMRegistrationRequest
        assertThat(req.request, IsInstanceOf(AddHSMCommand::class.java))
        val command = req.request as AddHSMCommand
        assertSame(config, command.config)
        assertNotNull(command.context)
        assertNotNull(command.context.items)
        assertThat(command.context.items, empty())
        val context = req.context
        assertEquals(CryptoConsts.CLUSTER_TENANT_ID, context.tenantId)
        assertEquals(result.value.requestId, context.requestId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(HSMRegistrationPublisher::class.simpleName, context.requestingComponent)
        assertThat(context.other.items, empty())
    }

    @Test
    @Timeout(5)
    fun `Should publish command to assign HSM`() {
        val result = publisher.act {
            registrar.assignHSM(
                tenantId = "some-tenant",
                category = CryptoConsts.CryptoCategories.LEDGER,
                defaultSignatureScheme = EDDSA_ED25519_CODE_NAME
            )
        }
        assertThat(result.value.requestId, not(emptyOrNullString()))
        assertEquals(1, result.messages.size)
        assertEquals(1, result.messages.first().size)
        assertEquals(Schemas.Crypto.HSM_REGISTRATION_MESSAGE_TOPIC, result.firstRecord.topic)
        assertEquals(CryptoConsts.CLUSTER_TENANT_ID, result.firstRecord.key)
        assertThat(result.firstRecord.value, IsInstanceOf(HSMRegistrationRequest::class.java))
        val req = result.firstRecord.value as HSMRegistrationRequest
        assertThat(req.request, IsInstanceOf(AssignHSMCommand::class.java))
        val command = req.request as AssignHSMCommand
        assertEquals (CryptoConsts.CryptoCategories.LEDGER, command.category)
        assertEquals(EDDSA_ED25519_CODE_NAME, command.defaultSignatureScheme)
        assertNotNull(command.context)
        assertNotNull(command.context.items)
        assertThat(command.context.items, empty())
        val context = req.context
        assertEquals("some-tenant", context.tenantId)
        assertEquals(result.value.requestId, context.requestId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(HSMRegistrationPublisher::class.simpleName, context.requestingComponent)
        assertThat(context.other.items, empty())
    }

    @Test
    @Timeout(5)
    fun `Should publish command to assign Soft HSM`() {
        val result = publisher.act {
            registrar.assignSoftHSM(
                tenantId = "some-tenant",
                category = CryptoConsts.CryptoCategories.LEDGER,
                passphrase = "1234",
                defaultSignatureScheme = EDDSA_ED25519_CODE_NAME
            )
        }
        assertThat(result.value.requestId, not(emptyOrNullString()))
        assertEquals(1, result.messages.size)
        assertEquals(1, result.messages.first().size)
        assertEquals(Schemas.Crypto.HSM_REGISTRATION_MESSAGE_TOPIC, result.firstRecord.topic)
        assertEquals(CryptoConsts.CLUSTER_TENANT_ID, result.firstRecord.key)
        assertThat(result.firstRecord.value, IsInstanceOf(HSMRegistrationRequest::class.java))
        val req = result.firstRecord.value as HSMRegistrationRequest
        assertThat(req.request, IsInstanceOf(AssignSoftHSMCommand::class.java))
        val command = req.request as AssignSoftHSMCommand
        assertEquals (CryptoConsts.CryptoCategories.LEDGER, command.category)
        assertEquals ("1234", command.passphrase)
        assertEquals(EDDSA_ED25519_CODE_NAME, command.defaultSignatureScheme)
        assertNotNull(command.context)
        assertNotNull(command.context.items)
        assertThat(command.context.items, empty())
        val context = req.context
        assertEquals("some-tenant", context.tenantId)
        assertEquals(result.value.requestId, context.requestId)
        result.assertThatIsBetween(context.requestTimestamp)
        assertEquals(HSMRegistrationPublisher::class.simpleName, context.requestingComponent)
        assertThat(context.other.items, empty())
    }
}