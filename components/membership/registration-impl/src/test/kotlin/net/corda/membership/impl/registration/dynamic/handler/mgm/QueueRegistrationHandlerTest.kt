package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.SignedData
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.QueueRegistration
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.v2.SetOwnRegistrationStatus
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.Verifier
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.records.Record
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

class QueueRegistrationHandlerTest {
    private companion object {
        val clock = TestClock(Instant.ofEpochSecond(0))
        const val TOPIC = "topic"
        const val KEY = "key"
        const val SERIAL = 1L
    }
    private val registrationId = UUID.randomUUID().toString()
    private val groupId = UUID.randomUUID()
    private val aliceName = MemberX500Name("Alice", "London", "GB")
    private val mgmName = MemberX500Name("MGM", "London", "GB")
    private val member = HoldingIdentity(aliceName.toString(), groupId.toString())
    private val mgm = HoldingIdentity(mgmName.toString(), groupId.toString())
    private val encodedSessionKey1 = "BBC123456789"
    private val encodedSessionKey2 = "BBC123456786"
    private val sessionKey1 = mock<PublicKey>()
    private val sessionKey2 = mock<PublicKey>()

    private val serialisedMemberContext = byteArrayOf(0)
    private val memberContextList = KeyValuePairList(
        listOf(
            KeyValuePair(PLATFORM_VERSION, "50100"),
            KeyValuePair(PARTY_SESSION_KEYS_PEM.format(0), encodedSessionKey1),
            KeyValuePair(PARTY_SESSION_KEYS_PEM.format(1), encodedSessionKey2),
        )
    )
    private val signature = mock<CryptoSignatureWithKey>()
    private val signatureSpec = mock<CryptoSignatureSpec>()
    private val memberContext = mock<SignedData> {
        on { data } doReturn ByteBuffer.wrap(serialisedMemberContext)
        on { signature } doReturn signature
        on { signatureSpec } doReturn signatureSpec
    }
    private val registrationContext = mock<SignedData>()

    private val registrationRequest =
        MembershipRegistrationRequest(registrationId, memberContext, registrationContext, SERIAL)

    private val mockPersistenceOperation = mock<MembershipPersistenceOperation<Unit>>()
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            persistRegistrationRequest(
                eq(mgm.toCorda()),
                eq(
                    RegistrationRequest(
                        RegistrationStatus.RECEIVED_BY_MGM,
                        registrationRequest.registrationId,
                        member.toCorda(),
                        registrationRequest.memberContext,
                        registrationRequest.registrationContext,
                        registrationRequest.serial,
                    )
                )
            )
        } doReturn mockPersistenceOperation
    }
    private val inputCommand = RegistrationCommand(QueueRegistration(mgm, member, registrationRequest, 0))

    private val deserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(eq(serialisedMemberContext)) } doReturn memberContextList
    }

    val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
    }

    private val signatureVerificationService = mock<SignatureVerificationService>()
    private val keyEncodingService = mock<KeyEncodingService> {
        on { decodePublicKey(encodedSessionKey1) } doReturn sessionKey1
        on { decodePublicKey(encodedSessionKey2) } doReturn sessionKey2
    }

    private val authenticatedMessageRecord = mock<Record<String, AppMessage>>()
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(any(), any(), any(), anyOrNull(), any(), any())
        } doReturn authenticatedMessageRecord
    }

    private val verifier = mock<Verifier> {
        on {
            verify(
                listOf(sessionKey1, sessionKey2),
                memberContext.signature,
                memberContext.signatureSpec,
                memberContext.data.array(),
            )
        } doAnswer {}
    }

    private val handler = QueueRegistrationHandler(
        clock,
        membershipPersistenceClient,
        cordaAvroSerializationFactory,
        signatureVerificationService,
        keyEncodingService,
        p2pRecordsFactory,
        verifier,
    )

    @Test
    fun `invoke returns check pending registration command as next step`() {
        whenever(mockPersistenceOperation.execute()).thenReturn(MembershipPersistenceResult.success())
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNull()
            assertThat(outputStates.size).isEqualTo(2)

            val registrationCommand = outputStates.firstNotNullOf { it.value as? RegistrationCommand }
            assertThat(registrationCommand).isNotNull
            assertThat(registrationCommand.command).isInstanceOf(CheckForPendingRegistration::class.java)
            val checkForPendingRegistrationCommand = registrationCommand.command as CheckForPendingRegistration
            assertThat(checkForPendingRegistrationCommand.mgm).isEqualTo(mgm)
            assertThat(checkForPendingRegistrationCommand.member).isEqualTo(member)
            assertThat(checkForPendingRegistrationCommand.numberOfRetriesSoFar).isEqualTo(0)
        }
    }

    @Test
    fun `invoke creates update registration request status command for the pending member`() {
        whenever(mockPersistenceOperation.execute()).thenReturn(MembershipPersistenceResult.success())
        val outputStates = handler.invoke(null, Record(TOPIC, KEY, inputCommand)).outputStates
        assertThat(outputStates).contains(authenticatedMessageRecord)
        verify(p2pRecordsFactory).createAuthenticatedMessageRecord(
            eq(mgm),
            eq(member),
            eq(
                SetOwnRegistrationStatus(
                    registrationId,
                    RegistrationStatus.RECEIVED_BY_MGM,
                    null
                )
            ),
            eq(5),
            any(),
            eq(MembershipStatusFilter.PENDING),
        )
    }

    @Test
    fun `retry if queueing the request failed`() {
        whenever(mockPersistenceOperation.getOrThrow()).thenThrow(
            MembershipPersistenceResult.PersistenceRequestException(MembershipPersistenceResult.Failure<Unit>("error"))
        )
        whenever(membershipPersistenceClient.persistRegistrationRequest(any(), any())).thenReturn(mockPersistenceOperation)
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNull()
            assertThat(outputStates.size).isEqualTo(1)
            assertThat(outputStates.first().value).isInstanceOf(RegistrationCommand::class.java)
            val registrationCommand = outputStates.first().value as RegistrationCommand
            assertThat(registrationCommand.command).isInstanceOf(QueueRegistration::class.java)
            val outputCommand = registrationCommand.command as QueueRegistration
            assertThat(outputCommand.mgm).isEqualTo(mgm)
            assertThat(outputCommand.member).isEqualTo(member)
            assertThat(outputCommand.memberRegistrationRequest).isEqualTo(registrationRequest)
            assertThat(outputCommand.numberOfRetriesSoFar).isEqualTo(1)
        }
    }

    @Test
    fun `retry if deserialising the context failed`() {
        whenever(mockPersistenceOperation.execute()).thenReturn(MembershipPersistenceResult.success())
        whenever(deserializer.deserialize(any())).doReturn(null)
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNull()
            assertThat(outputStates.size).isEqualTo(1)
            assertThat(outputStates.first().value).isInstanceOf(RegistrationCommand::class.java)
            val registrationCommand = outputStates.first().value as RegistrationCommand
            assertThat(registrationCommand.command).isInstanceOf(QueueRegistration::class.java)
            val outputCommand = registrationCommand.command as QueueRegistration
            assertThat(outputCommand.mgm).isEqualTo(mgm)
            assertThat(outputCommand.member).isEqualTo(member)
            assertThat(outputCommand.memberRegistrationRequest).isEqualTo(registrationRequest)
            assertThat(outputCommand.numberOfRetriesSoFar).isEqualTo(1)
        }
    }

    @Test
    fun `discard if max retries exceeded`() {
        val inputCommand = RegistrationCommand(QueueRegistration(mgm, member, registrationRequest, 10))
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNull()
            assertThat(outputStates).isEmpty()
        }
    }

    @Test
    fun `discard if signature verification failed`() {
        whenever(
            verifier.verify(
                listOf(sessionKey1, sessionKey2),
                memberContext.signature,
                memberContext.signatureSpec,
                memberContext.data.array(),
            )
        ).doThrow(CryptoSignatureException("Invalid signature."))
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNull()
            assertThat(outputStates).isEmpty()
        }
    }
}
