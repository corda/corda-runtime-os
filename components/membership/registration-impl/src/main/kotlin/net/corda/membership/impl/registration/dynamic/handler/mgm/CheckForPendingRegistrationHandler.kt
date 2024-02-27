package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.RegistrationLogger
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
        val registrationLogger = RegistrationLogger(logger)
            .setMember(command.member)
            .setMgm(command.mgm)
        val (outputState, outputCommand) = try {
            if (command.numberOfRetriesSoFar < MAX_RETRIES) {
                state?.let {
                    registrationLogger.setRegistrationId(it.registrationId)
                    registrationLogger.info(
                        "There is a registration in progress for member. " +
                            "The service will wait until processing the previous request finishes."
                    )
                    Pair(state, null)
                } ?: run {
                    getNextRequest(command, registrationLogger)
                }
            } else {
                registrationLogger.warn(
                    "Max re-tries exceeded to get next registration request registration. Registration is discarded."
                )
                Pair(state, null)
            }
        } catch (ex: Exception) {
            registrationLogger.warn(
                "Exception happened while looking for the next request to process for member. " +
                    "Will re-try again.",
                ex
            )
            Pair(state, increaseNumberOfRetries(command))
        }
        return if (outputCommand != null) {
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

    private fun getNextRequest(
        command: CheckForPendingRegistration,
        registrationLogger: RegistrationLogger
    ): Pair<RegistrationState?, StartRegistration?> {
        registrationLogger.info("Looking for the next request for member.")
        val nextRequest = membershipQueryClient.queryRegistrationRequests(
            command.mgm.toCorda(),
            command.member.toCorda().x500Name,
            listOf(RegistrationStatus.RECEIVED_BY_MGM),
            1
        ).getOrThrow().firstOrNull()
        // need to check if there were any results at all
        return if (nextRequest != null) {
            registrationLogger.setRegistrationId(nextRequest.registrationId)
            registrationLogger.info("Retrieved next request for member from the database. Proceeding with registration.")
            // create state to make sure we process one registration at the same time
            Pair(RegistrationState(nextRequest.registrationId, command.member, command.mgm, emptyList()), StartRegistration())
        } else {
            registrationLogger.info("There are no registration requests queued for member.")
            Pair(null, null)
        }
    }

    private fun increaseNumberOfRetries(command: CheckForPendingRegistration) =
        CheckForPendingRegistration(command.mgm, command.member, command.numberOfRetriesSoFar + 1)
}
