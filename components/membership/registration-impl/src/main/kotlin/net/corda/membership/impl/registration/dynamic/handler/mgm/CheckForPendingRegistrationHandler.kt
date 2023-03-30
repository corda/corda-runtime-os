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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CheckForPendingRegistrationHandler(
    private val membershipQueryClient: MembershipQueryClient,
) : RegistrationHandler<CheckForPendingRegistration> {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MAX_RETRIES = 10
    }

    override val commandType = CheckForPendingRegistration::class.java

    private val lock = ReentrantLock()

    override fun invoke(state: RegistrationState?, key: String, command: CheckForPendingRegistration): RegistrationHandlerResult {
        return lock.withLock {
            prepareCommand(state, key, command)
        }
    }

    private fun prepareCommand(state: RegistrationState?, key: String, command: CheckForPendingRegistration): RegistrationHandlerResult {
        val (outputState, outputCommand) = try {
            if(command.numberOfRetriesSoFar < MAX_RETRIES) {
                state?.let {
                    logger.info("There is a registration in progress for member ${state.registeringMember}" +
                            " with ID `${state.registrationId}`. The service will wait until processing the previous " +
                            "request finishes.")
                    Pair(null, null)
                } ?: run {
                    getNextRequest(command)
                }
            } else {
                logger.warn(
                    "Max re-tries exceeded to get next registration request registration " +
                            "for member ${command.member.x500Name}." +
                            "Registration is discarded."
                )
                Pair(null, null)
            }
        } catch (ex: Exception) {
            logger.warn("Exception happened while looking for the next request to process. Will re-try again.", ex)
            Pair(
                null,
                CheckForPendingRegistration(command.mgm, command.member, command.numberOfRetriesSoFar + 1)
            )
        }
        return if(outputCommand != null) {
            RegistrationHandlerResult(
                // create state to make sure we process one registration at the same time
                outputState,
                listOf(
                    Record(Schemas.Membership.REGISTRATION_COMMAND_TOPIC, key, RegistrationCommand(outputCommand))
                )
            )
        } else {
            RegistrationHandlerResult(outputState, emptyList())
        }
    }

    private fun getNextRequest(command: CheckForPendingRegistration): Pair<RegistrationState?, StartRegistration?> {
        logger.info("Looking for the next request for member ${command.member.x500Name}.")
        val nextRequest = membershipQueryClient.queryRegistrationRequests(
            command.mgm.toCorda(),
            command.member.toCorda().x500Name,
            listOf(RegistrationStatus.RECEIVED_BY_MGM),
            1
        ).getOrThrow().firstOrNull()
        // need to check if there were any results at all
        return if(nextRequest != null) {
            logger.info("Retrieved next request for member ${command.member.x500Name} " +
                    "with ID `${nextRequest.registrationId}` from the database. Proceeding with registration.")
            Pair(RegistrationState(nextRequest.registrationId, command.member, command.mgm), StartRegistration())
        } else {
            logger.info("There are no registration requests queued " +
                    "for member ${command.member.x500Name}.")
            Pair(null, null)
        }
    }
}