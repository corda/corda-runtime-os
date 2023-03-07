package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.lib.toWire
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
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
    private val mockMemberContext: MemberContext = mock()
    private val memberInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn mockMemberContext
    }
    private val cordaAvroSerializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn "".toByteArray()
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn cordaAvroSerializer
    }
    private val membershipPersistenceClient: MembershipPersistenceClient = mock {
        on {
            persistRegistrationRequest(any(), any())
        } doReturn MembershipPersistenceResult.success()
    }
    private val membershipQueryClient = mock<MembershipQueryClient> {

    }
    private val mgmRegistrationRequestHandler = MGMRegistrationRequestHandler(
        cordaAvroSerializationFactory,
        membershipPersistenceClient,
        membershipQueryClient
    )

    @Test
    fun `persistRegistrationRequest sends request to persistence client`() {
        val serialisedPayload = "test".toByteArray()
        whenever(cordaAvroSerializer.serialize(any())).thenReturn(serialisedPayload)
        assertDoesNotThrow {
            mgmRegistrationRequestHandler.persistRegistrationRequest(
                registrationId,
                holdingIdentity,
                memberInfo
            )
        }

        val captor = argumentCaptor<RegistrationRequest>()
        verify(membershipPersistenceClient).persistRegistrationRequest(eq(holdingIdentity), captor.capture())
        assertThat(captor.firstValue.registrationId).isEqualTo(registrationId.toString())
        assertThat(captor.firstValue.memberContext).isEqualTo(ByteBuffer.wrap(serialisedPayload))
        assertThat(captor.firstValue.status).isEqualTo(RegistrationStatus.APPROVED)
        verify(cordaAvroSerializer).serialize(memberInfo.memberProvidedContext.toWire())
    }

    @Test
    fun `throwIfRegistrationAlreadyApproved sends request to the query client`() {
        whenever(membershipQueryClient.queryRegistrationRequestsStatus(holdingIdentity)).doReturn(
            MembershipQueryResult.Success(emptyList())
        )
        mgmRegistrationRequestHandler.throwIfRegistrationAlreadyApproved(holdingIdentity)
        verify(membershipQueryClient).queryRegistrationRequestsStatus(
            eq(holdingIdentity), eq(null), eq(RegistrationStatus.values().toList())
        )
    }

    @Test
    fun `expected exception thrown if registration request persistence fails`() {
        whenever(membershipPersistenceClient.persistRegistrationRequest(eq(holdingIdentity), any())).
            doReturn(MembershipPersistenceResult.Failure(""))
        val serialisedPayload = "test".toByteArray()
        whenever(cordaAvroSerializer.serialize(any())).thenReturn(serialisedPayload)

        assertThrows<InvalidMembershipRegistrationException> {
            mgmRegistrationRequestHandler.persistRegistrationRequest(
                registrationId,
                holdingIdentity,
                memberInfo
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
                memberInfo
            )
        }
    }

    @Test
    fun `expected exception thrown if registration already approved for holding id`() {
        val persistedRegistrationRequest = mock<RegistrationRequestStatus> {
            on {status} doReturn RegistrationStatus.APPROVED
        }
        whenever(membershipQueryClient.queryRegistrationRequestsStatus(holdingIdentity)).doReturn(
            MembershipQueryResult.Success(listOf(persistedRegistrationRequest))
        )
        assertThrows<InvalidMembershipRegistrationException> {
            mgmRegistrationRequestHandler.throwIfRegistrationAlreadyApproved(holdingIdentity)
        }
    }
}