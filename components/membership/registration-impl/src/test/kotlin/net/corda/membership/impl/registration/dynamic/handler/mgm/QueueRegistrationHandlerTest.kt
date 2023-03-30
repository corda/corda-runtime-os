package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.QueueRegistration
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.records.Record
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.UUID

class QueueRegistrationHandlerTest {
    private companion object {
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

    private val memberContext = mock<ByteBuffer>()
    private val memberSignature = mock<CryptoSignatureWithKey>()
    private val memberSignatureSpec = mock<CryptoSignatureSpec>()

    private val registrationRequest =
        MembershipRegistrationRequest(registrationId, memberContext, memberSignature, memberSignatureSpec, SERIAL)
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            persistRegistrationRequest(
                eq(mgm.toCorda()),
                eq(RegistrationRequest(
                    RegistrationStatus.RECEIVED_BY_MGM,
                    registrationRequest.registrationId,
                    member.toCorda(),
                    registrationRequest.memberContext,
                    registrationRequest.memberSignature,
                    registrationRequest.memberSignatureSpec,
                    registrationRequest.serial,
                ))
            )
        } doReturn MembershipPersistenceResult.success()
    }
    private val inputCommand = RegistrationCommand(QueueRegistration(mgm, member, registrationRequest, 0))

    private val handler = QueueRegistrationHandler(membershipPersistenceClient)

    @Test
    fun `invoke returns check pending registration command as next step`() {
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(outputStates.size).isEqualTo(1)
            assertThat(outputStates.first().value).isInstanceOf(RegistrationCommand::class.java)
            val registrationCommand = outputStates.first().value as RegistrationCommand
            assertThat(registrationCommand.command).isInstanceOf(CheckForPendingRegistration::class.java)
            val outputCommand = registrationCommand.command as CheckForPendingRegistration
            assertThat(outputCommand.mgm).isEqualTo(mgm)
            assertThat(outputCommand.registeringMember).isEqualTo(member)
        }
    }

    @Test
    fun `retry if queueing the request failed`() {
        whenever(membershipPersistenceClient.persistRegistrationRequest(any(), any()))
            .thenReturn(MembershipPersistenceResult.Failure("error happened"))
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(outputStates.size).isEqualTo(1)
            assertThat(outputStates.first().value).isInstanceOf(RegistrationCommand::class.java)
            val registrationCommand = outputStates.first().value as RegistrationCommand
            assertThat(registrationCommand.command).isInstanceOf(QueueRegistration::class.java)
        }
    }
}