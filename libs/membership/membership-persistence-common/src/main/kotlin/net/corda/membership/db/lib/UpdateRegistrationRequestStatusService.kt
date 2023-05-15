package net.corda.membership.db.lib

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_DB_ASYNC_TOPIC
import net.corda.utilities.time.Clock
import java.util.UUID

class UpdateRegistrationRequestStatusService(
    private val clock: Clock,
) {
    fun createCommand(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus,
        reason: String = "Update status to $registrationRequestStatus",
    ): Collection<Record<*, *>> {
        val request = MembershipPersistenceRequest(
            MembershipRequestContext(
                clock.instant(),
                UUID.randomUUID().toString(),
                viewOwningIdentity,
            ),
            UpdateRegistrationRequestStatus(
                registrationId,
                registrationRequestStatus,
                reason,
            ),
        )

        return listOf(
            Record(
                topic = MEMBERSHIP_DB_ASYNC_TOPIC,
                key = request.context.requestId,
                value = MembershipPersistenceAsyncRequest(request),
            ),
        )
    }
}
