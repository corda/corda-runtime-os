package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.toWire
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.UUID

class MgmRegistrationRequestHandlerTest {

    private val registrationId = UUID(0, 1)
    private val holdingIdentity = HoldingIdentity(
        MemberX500Name.parse("O=Alice, L=London, C=GB"),
        UUID(0, 1).toString()
    )
    private val memberContext = mapOf("key" to "value")
    private val signature = CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf()), ByteBuffer.wrap(byteArrayOf()))
    private val signatureSpec = CryptoSignatureSpec("", null, null)
    private val cordaAvroSerializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn "".toByteArray()
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn cordaAvroSerializer
    }
    private val operation = mock<MembershipPersistenceOperation<Unit>> {
        on { execute() } doReturn MembershipPersistenceResult.success()
    }
    private val membershipPersistenceClient: MembershipPersistenceClient = mock {
        on {
            persistRegistrationRequest(any(), any())
        } doReturn operation
    }
    private val membershipQueryClient = mock<MembershipQueryClient>()
    private val mgmRegistrationRequestHandler = MGMRegistrationRequestHandler(
        cordaAvroSerializationFactory,
        membershipPersistenceClient,
        membershipQueryClient,
    )

    @Test
    fun `persistRegistrationRequest sends request to persistence client`() {
        val serialisedPayload = "test1".toByteArray()
        val serializedMemberContext = "1".toByteArray()
        whenever(cordaAvroSerializer.serialize(eq(memberContext.toWire()))).thenReturn(serializedMemberContext)
        whenever(cordaAvroSerializer.serialize(eq(KeyValuePairList(emptyList())))).thenReturn(serialisedPayload)
        assertDoesNotThrow {
            mgmRegistrationRequestHandler.persistRegistrationRequest(
                registrationId,
                holdingIdentity,
                memberContext,
            )
        }

        val captor = argumentCaptor<RegistrationRequest>()
        verify(membershipPersistenceClient).persistRegistrationRequest(eq(holdingIdentity), captor.capture())
        assertThat(captor.firstValue.registrationId).isEqualTo(registrationId.toString())
        assertThat(captor.firstValue.memberContext.data).isEqualTo(ByteBuffer.wrap(serializedMemberContext))
        assertThat(captor.firstValue.registrationContext.data).isEqualTo(ByteBuffer.wrap(serialisedPayload))
        assertThat(captor.firstValue.status).isEqualTo(RegistrationStatus.APPROVED)
        assertThat(captor.firstValue.serial).isEqualTo(0)
        assertThat(captor.firstValue.memberContext.signature).isEqualTo(signature)
        assertThat(captor.firstValue.memberContext.signatureSpec).isEqualTo(signatureSpec)
    }

    @Test
    fun `throwIfRegistrationAlreadyApproved sends request to the query client`() {
        whenever(membershipQueryClient.queryRegistrationRequests(holdingIdentity)).doReturn(
            MembershipQueryResult.Success(emptyList())
        )
        mgmRegistrationRequestHandler.throwIfRegistrationAlreadyApproved(holdingIdentity)
        verify(membershipQueryClient).queryRegistrationRequests(
            eq(holdingIdentity),
            eq(null),
            eq(RegistrationStatus.values().toList()),
            eq(null)
        )
    }

    @Test
    fun `expected exception thrown if registration request persistence fails`() {
        whenever(operation.execute()).doReturn(MembershipPersistenceResult.Failure(""))
        val serialisedPayload = "test".toByteArray()
        whenever(cordaAvroSerializer.serialize(any())).thenReturn(serialisedPayload)

        assertThrows<InvalidMembershipRegistrationException> {
            mgmRegistrationRequestHandler.persistRegistrationRequest(
                registrationId,
                holdingIdentity,
                memberContext,
            )
        }
    }

    @Test
    fun `expected exception thrown if serializing the registration request fails`() {
        whenever(cordaAvroSerializer.serialize(any())).doReturn(null)

        assertThrows<InvalidMembershipRegistrationException> {
            mgmRegistrationRequestHandler.persistRegistrationRequest(
                registrationId,
                holdingIdentity,
                memberContext,
            )
        }
    }

    @Test
    fun `expected exception thrown if registration already approved for holding id`() {
        val persistedRegistrationRequest = mock<RegistrationRequestDetails> {
            on { registrationStatus } doReturn RegistrationStatus.APPROVED
        }
        whenever(membershipQueryClient.queryRegistrationRequests(holdingIdentity)).doReturn(
            MembershipQueryResult.Success(listOf(persistedRegistrationRequest))
        )
        assertThrows<InvalidMembershipRegistrationException> {
            mgmRegistrationRequestHandler.throwIfRegistrationAlreadyApproved(holdingIdentity)
        }
    }

    @Test
    fun `persistRegistrationRequest persists request with the provided serial`() {
        val serial = 10L
        assertDoesNotThrow {
            mgmRegistrationRequestHandler.persistRegistrationRequest(
                registrationId,
                holdingIdentity,
                memberContext,
                serial,
            )
        }
        val captor = argumentCaptor<RegistrationRequest>()
        verify(membershipPersistenceClient).persistRegistrationRequest(eq(holdingIdentity), captor.capture())
        assertThat(captor.firstValue.serial).isEqualTo(serial)
    }

    @Test
    fun `retrieving latest registration request is successful`() {
        val oldRequest = mock<RegistrationRequestDetails> {
            on { serial } doReturn 1
        }
        val newRequest = mock<RegistrationRequestDetails> {
            on { serial } doReturn 2
        }
        whenever(
            membershipQueryClient.queryRegistrationRequests(
                eq(holdingIdentity),
                eq(holdingIdentity.x500Name),
                eq(listOf(RegistrationStatus.APPROVED)),
                isNull()
            )
        ).thenReturn(MembershipQueryResult.Success(listOf(oldRequest, newRequest)))
        assertThat(mgmRegistrationRequestHandler.getLastRegistrationRequest(holdingIdentity)).isEqualTo(newRequest)
    }
}
