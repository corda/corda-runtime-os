package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

class CheckForPendingRegistrationHandler(
    private val membershipQueryClient: MembershipQueryClient,
) : RegistrationHandler<CheckForPendingRegistration> {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val commandType = CheckForPendingRegistration::class.java

    override fun invoke(state: RegistrationState?, key: String, command: CheckForPendingRegistration): RegistrationHandlerResult {
        val outputCommand = try {
            state?.let {
                null
            } ?: run {
                logger.info("Looking for the next request for member ${command.registeringMember.x500Name}.")
                val nextRequest = membershipQueryClient.queryRegistrationRequests(
                    command.mgm.toCorda(),
                    command.registeringMember.toCorda().x500Name,
                    listOf(RegistrationStatus.RECEIVED_BY_MGM),
                    1
                ).getOrThrow().firstOrNull()
                // need to check if there were any results at all
                if(nextRequest != null) {
                    logger.info("Retrieved next request for member ${command.registeringMember.x500Name} " +
                            "with ID `${nextRequest.registrationId}` from the database. Proceeding with registration.")
                    StartRegistration()
                } else {
                    logger.info("There are no registration requests queued " +
                            "for member ${command.registeringMember.x500Name}.")
                    null
                }
            }
        } catch (ex: Exception) {
            logger.warn("Exception happened while looking for the next request to process. Will re-try again.", ex)
            command
        }
        return if(outputCommand != null) {
            RegistrationHandlerResult(
                state,
                listOf(
                    Record(Schemas.Membership.REGISTRATION_COMMAND_TOPIC, key, RegistrationCommand(outputCommand))
                )
            )
        } else {
            RegistrationHandlerResult(null, emptyList())
        }
    }
}