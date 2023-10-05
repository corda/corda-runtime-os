package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CheckForPendingRegistrationHandler(
    private val membershipQueryClient: MembershipQueryClient,
) : RegistrationHandler<CheckForPendingRegistration> {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MAX_RETRIES = 10
    }

    override val commandType = CheckForPendingRegistration::class.java

    override fun getOwnerHoldingId(state: RegistrationState?, command: CheckForPendingRegistration) = state?.mgm

    override fun invoke(state: RegistrationState?, key: String, command: CheckForPendingRegistration): RegistrationHandlerResult {
        val (outputState, outputCommand) = try {
            if(command.numberOfRetriesSoFar < MAX_RETRIES) {
                state?.let {
                    logger.info("There is a registration in progress for member ${state.registeringMember.x500Name} " +
                            "from group `${state.registeringMember.groupId}` " +
                            "with ID `${state.registrationId}`. The service will wait until processing the previous " +
                            "request finishes.")
                    Pair(state, null)
                } ?: run {
                    getNextRequest(command)
                }
            } else {
                logger.warn(
                    "Max re-tries exceeded to get next registration request registration " +
                            "for member ${command.member.x500Name} from group `${command.member.groupId}`. " +
                            "Registration is discarded."
                )
                Pair(state, null)
            }
        } catch (ex: Exception) {
            logger.warn("Exception happened while looking for the next request to process for member " +
                    "${command.member.x500Name} from group `${command.member.groupId}`. Will re-try again.", ex)
            Pair(state, increaseNumberOfRetries(command))
        }
        return if(outputCommand != null) {
            RegistrationHandlerResult(
                outputState,
                listOf(
                    Record(REGISTRATION_COMMAND_TOPIC, key, RegistrationCommand(outputCommand))
                )
            )
        } else {
            RegistrationHandlerResult(outputState, emptyList())
        }
    }

    private fun getNextRequest(command: CheckForPendingRegistration): Pair<RegistrationState?, StartRegistration?> {
        logger.info("Looking for the next request for member ${command.member.x500Name} from " +
                "group `${command.member.groupId}`.")
        val nextRequest = membershipQueryClient.queryRegistrationRequests(
            command.mgm.toCorda(),
            command.member.toCorda().x500Name,
            listOf(RegistrationStatus.RECEIVED_BY_MGM),
            1
        ).getOrThrow().firstOrNull()
        // need to check if there were any results at all
        return if(nextRequest != null) {
            logger.info("Retrieved next request for member ${command.member.x500Name} from " +
                    "group `${command.member.groupId}` " +
                    "with ID `${nextRequest.registrationId}` from the database. Proceeding with registration.")
            // create state to make sure we process one registration at the same time
            Pair(RegistrationState(nextRequest.registrationId, command.member, command.mgm, emptyList()), StartRegistration())
        } else {
            logger.info("There are no registration requests queued " +
                    "for member ${command.member.x500Name} from group `${command.member.groupId}`.")
            Pair(null, null)
        }
    }

    private fun increaseNumberOfRetries(command: CheckForPendingRegistration) =
        CheckForPendingRegistration(command.mgm, command.member, command.numberOfRetriesSoFar + 1)
}