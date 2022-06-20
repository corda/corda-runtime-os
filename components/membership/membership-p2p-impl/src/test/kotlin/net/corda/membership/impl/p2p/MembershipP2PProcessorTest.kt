package net.corda.membership.impl.p2p

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.StartRegistration
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.membership.impl.p2p.MembershipP2PProcessor.Companion.MEMBERSHIP_P2P_SUBSYSTEM
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.*

class MembershipP2PProcessorTest {

    private fun String.toByteBuffer() = ByteBuffer.wrap(toByteArray())
    private val avroSchemaRegistry: AvroSchemaRegistry = mock()

    private val memberContext = KeyValuePairList(listOf(KeyValuePair("foo", "bar")))
    private val testSig =
        CryptoSignatureWithKey("ABC".toByteBuffer(), "DEF".toByteBuffer(), KeyValuePairList(emptyList()))
    private val registrationRequest = MembershipRegistrationRequest(
        UUID.randomUUID().toString(),
        memberContext.toByteBuffer(),
        testSig
    )
    private val validMsgPayload = registrationRequest.toByteBuffer()

    private val groupId = UUID.randomUUID().toString()
    private val source = HoldingIdentity("C=GB, L=London, O=Alice", groupId)
    private val destination = HoldingIdentity("C=GB, L=London, O=MGM", groupId)

    private lateinit var membershipP2PProcessor: MembershipP2PProcessor

    @BeforeEach
    fun setUp() {
        membershipP2PProcessor = MembershipP2PProcessor(avroSchemaRegistry)
    }

    @Test
    fun `empty input results in empty output`() {
        val result = membershipP2PProcessor.onNext(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `null value in input record results in empty output`() {
        val result = membershipP2PProcessor.onNext(listOf(Record("foo", "bar", null)))
        assertThat(result).isEmpty()
    }

    @Test
    fun `Registration request as unauthenticate message is processed as expected`() {

        val appMessage = with(validMsgPayload) {
            mockPayloadDeserialization()
            asUnauthenticatedAppMessagePayload()
        }
        val result = membershipP2PProcessor.onNext(listOf(Record("foo", "bar", appMessage)))

        assertThat(result)
            .isNotEmpty
            .hasSize(1)
        assertThat(result.first().topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
        assertThat(result.first().value).isInstanceOf(RegistrationCommand::class.java)
        assertThat(result.first().key).isEqualTo(source.toCorda().id)

        val value = result.first().value as RegistrationCommand
        assertThat(value.command).isInstanceOf(StartRegistration::class.java)
        val command = value.command as StartRegistration
        assertThat(command.destination).isEqualTo(destination)
        assertThat(command.source).isEqualTo(source)
        assertThat(command.memberRegistrationRequest).isEqualTo(registrationRequest)
    }

    @Test
    fun `Registration request on a non-membership subsystem returns no output records`() {

        val appMessage = with(validMsgPayload) {
            mockPayloadDeserialization()
            asUnauthenticatedAppMessagePayload("BAD_SUBSYSTEM")
        }
        val result = membershipP2PProcessor.onNext(listOf(Record("foo", "bar", appMessage)))

        assertThat(result).isEmpty()
    }

    @Test
    fun `Registration request as authenticated message throws exception`() {

        val appMessage = with(validMsgPayload) {
            mockPayloadDeserialization()
            asAuthenticatedAppMessagePayload()
        }
        val key = "bar"
        assertThrows<UnsupportedOperationException> {
            membershipP2PProcessor.onNext(listOf(Record("foo", key, appMessage)))
        }

    }

    @Test
    fun `Message payload with no handler returns no output records`() {

        val appMessage = with("badPayload".toByteBuffer()) {
            whenever(avroSchemaRegistry.getClassType(eq(this))).thenReturn(String::class.java)
            asUnauthenticatedAppMessagePayload()
        }
        val result = membershipP2PProcessor.onNext(listOf(Record("foo", "bar", appMessage)))

        assertThat(result).isEmpty()
    }

    private fun ByteBuffer.asUnauthenticatedAppMessagePayload(subsystem: String = MEMBERSHIP_P2P_SUBSYSTEM): AppMessage {
        return AppMessage(
            UnauthenticatedMessage(
                UnauthenticatedMessageHeader(
                    destination, source, subsystem
                ),
                this
            )
        )
    }

    private fun ByteBuffer.asAuthenticatedAppMessagePayload(): AppMessage {
        return AppMessage(
            AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    destination, source, 1000L, "mid", "tid", MEMBERSHIP_P2P_SUBSYSTEM
                ),
                this
            )
        )
    }

    private fun ByteBuffer.mockPayloadDeserialization() {
        whenever(avroSchemaRegistry.getClassType(eq(this))).thenReturn(MembershipRegistrationRequest::class.java)
        whenever(
            avroSchemaRegistry.deserialize(
                eq(this),
                eq(MembershipRegistrationRequest::class.java),
                eq(null)
            )
        ).thenReturn(registrationRequest)
    }
}