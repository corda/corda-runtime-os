package net.corda.membership.impl.p2p

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.StartRegistration
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.membership.impl.p2p.MembershipP2PProcessor.Companion.MEMBERSHIP_P2P_SUBSYSTEM
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_VERIFICATION_TOPIC
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.UUID

class MembershipP2PProcessorTest {

    private companion object {
        const val TOPIC = "foo"
        const val KEY = "bar"
    }

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
    private val registrationReqMsgPayload = registrationRequest.toByteBuffer()

    private val groupId = "1f5e558c-dd87-438f-a57f-21e69c1e0b88"
    private val member = HoldingIdentity("C=GB, L=London, O=Alice", groupId)
    private val mgm = HoldingIdentity("C=GB, L=London, O=MGM", groupId)

    private val clock = UTCClock()
    private val verificationRequest = VerificationRequest(
        member,
        mgm,
        UUID.randomUUID().toString(),
        clock.instant(),
        KeyValuePairList(listOf(KeyValuePair("A", "B")))
    )

    private val verificationReqMsgPayload = verificationRequest.toByteBuffer()

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
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, null)))
        assertThat(result).isEmpty()
    }

    @Test
    fun `Registration request as unauthenticated message is processed as expected`() {
        val appMessage = with(registrationReqMsgPayload) {
            mockPayloadDeserialization()
            asUnauthenticatedAppMessagePayload()
        }
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))

        assertThat(result)
            .isNotEmpty
            .hasSize(1)
        assertThat(result.first().topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
        assertThat(result.first().value).isInstanceOf(RegistrationCommand::class.java)
        assertThat(result.first().key).isEqualTo(member.toCorda().id)

        val value = result.first().value as RegistrationCommand
        assertThat(value.command).isInstanceOf(StartRegistration::class.java)
        val command = value.command as StartRegistration
        assertThat(command.destination).isEqualTo(mgm)
        assertThat(command.source).isEqualTo(member)
        assertThat(command.memberRegistrationRequest).isEqualTo(registrationRequest)
    }

    @Test
    fun `Registration request on a non-membership subsystem returns no output records`() {
        val appMessage = with(registrationReqMsgPayload) {
            mockPayloadDeserialization()
            asUnauthenticatedAppMessagePayload(mgm, member, "BAD_SUBSYSTEM")
        }
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))

        assertThat(result).isEmpty()
    }

    @Test
    fun `Registration request as authenticated message throws exception`() {
        val appMessage = with(registrationReqMsgPayload) {
            mockPayloadDeserialization()
            asAuthenticatedAppMessagePayload()
        }
        assertThrows<UnsupportedOperationException> {
            membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))
        }

    }

    @Test
    fun `Message payload with no handler returns no output records`() {
        val appMessage = with("badPayload".toByteBuffer()) {
            whenever(avroSchemaRegistry.getClassType(eq(this))).thenReturn(String::class.java)
            asUnauthenticatedAppMessagePayload()
        }
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))

        assertThat(result).isEmpty()
    }

    @Test
    fun `Verification request as authenticated message is processed as expected`() {
        val appMessage = with(verificationReqMsgPayload) {
            mockPayloadDeserialization()
            asAuthenticatedAppMessagePayload(member, mgm)
        }
        val result = membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))

        assertThat(result)
            .isNotEmpty
            .hasSize(1)
        assertThat(result.first().topic).isEqualTo(MEMBERSHIP_VERIFICATION_TOPIC)
        assertThat(result.first().value).isInstanceOf(VerificationRequest::class.java)
        assertThat(result.first().key).isEqualTo(member.toCorda().id)
    }

    @Test
    fun `Verification request as unauthenticated message throws exception`() {
        val appMessage = with(verificationReqMsgPayload) {
            mockPayloadDeserialization()
            asUnauthenticatedAppMessagePayload(member, mgm)
        }
        assertThrows<UnsupportedOperationException> {
            membershipP2PProcessor.onNext(listOf(Record(TOPIC, KEY, appMessage)))
        }
    }

    private fun ByteBuffer.asUnauthenticatedAppMessagePayload(
        destination: HoldingIdentity = mgm,
        source: HoldingIdentity = member,
        subsystem: String = MEMBERSHIP_P2P_SUBSYSTEM
    ): AppMessage {
        return AppMessage(
            UnauthenticatedMessage(
                UnauthenticatedMessageHeader(
                    destination, source, subsystem
                ),
                this
            )
        )
    }

    private fun ByteBuffer.asAuthenticatedAppMessagePayload(
        destination: HoldingIdentity = mgm,
        source: HoldingIdentity = member
    ): AppMessage {
        return AppMessage(
            AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    destination, source, 1000L, "mid", "tid", MEMBERSHIP_P2P_SUBSYSTEM
                ),
                this
            )
        )
    }

    private fun mockPayloadDeserialization() {
        whenever(avroSchemaRegistry.getClassType(eq(registrationReqMsgPayload))).thenReturn(MembershipRegistrationRequest::class.java)
        whenever(
            avroSchemaRegistry.deserialize(
                eq(registrationReqMsgPayload),
                eq(MembershipRegistrationRequest::class.java),
                eq(null)
            )
        ).thenReturn(registrationRequest)
        whenever(avroSchemaRegistry.getClassType(eq(verificationReqMsgPayload))).thenReturn(VerificationRequest::class.java)
        whenever(
            avroSchemaRegistry.deserialize(
                eq(verificationReqMsgPayload),
                eq(VerificationRequest::class.java),
                eq(null)
            )
        ).thenReturn(verificationRequest)
    }
}