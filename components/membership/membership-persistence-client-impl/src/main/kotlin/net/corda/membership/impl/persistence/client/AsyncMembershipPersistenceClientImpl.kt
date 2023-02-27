package net.corda.membership.impl.persistence.client

import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToDeclined
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.AsyncMembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_DB_ASYNC_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import java.util.UUID

internal class AsyncMembershipPersistenceClientImpl(
    private val clock: Clock,
) : AsyncMembershipPersistenceClient {
    private fun <T> createRecords(holdingIdentity: HoldingIdentity, request: T): Collection<Record<*, *>> {
        val requestId = UUID.randomUUID().toString()
        return listOf(
            Record(
                topic = MEMBERSHIP_DB_ASYNC_TOPIC,
                key = requestId,
                value = MembershipPersistenceAsyncRequest(
                    MembershipRequestContext(
                        clock.instant(),
                        requestId,
                        holdingIdentity.toAvro(),
                    ),
                    request
                ),

            )
        )
    }

    override fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>,
    ): Collection<Record<*, *>> {
        return createRecords(
            viewOwningIdentity,
            PersistMemberInfo(
                memberInfos.map {
                    PersistentMemberInfo(
                        viewOwningIdentity.toAvro(),
                        it.memberProvidedContext.toAvro(),
                        it.mgmProvidedContext.toAvro(),
                    )
                }
            )
        )
    }

    override fun createPersistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest,
    ): Collection<Record<*, *>> {
        return createRecords(
            viewOwningIdentity,
            PersistRegistrationRequest(
                registrationRequest.status,
                registrationRequest.requester.toAvro(),
                with(registrationRequest) {
                    MembershipRegistrationRequest(
                        registrationId,
                        memberContext,
                        signature,
                    )
                }
            )
        )
    }

    override fun setMemberAndRegistrationRequestAsApprovedRequest(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ): Collection<Record<*, *>> {
        return createRecords(
            viewOwningIdentity,
            UpdateMemberAndRegistrationRequestToApproved(
                approvedMember.toAvro(),
                registrationRequestId
            )
        )
    }

    override fun setMemberAndRegistrationRequestAsDeclinedRequest(
        viewOwningIdentity: HoldingIdentity,
        declinedMember: HoldingIdentity,
        registrationRequestId: String,
    ): Collection<Record<*, *>> {
        return createRecords(
            viewOwningIdentity,
            UpdateMemberAndRegistrationRequestToDeclined(
                declinedMember.toAvro(),
                registrationRequestId
            )
        )
    }

    override fun setRegistrationRequestStatusRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus,
        reason: String?,
    ): Collection<Record<*, *>> {
        return createRecords(
            viewOwningIdentity,
            UpdateRegistrationRequestStatus(registrationId, registrationRequestStatus, reason)
        )
    }
}
