package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.QueueRegistration
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class QueueRegistrationHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient,
) : RegistrationHandler<QueueRegistration> {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MAX_RETRIES = 10
    }

    override val commandType = QueueRegistration::class.java

    override fun invoke(state: RegistrationState?, key: String, command: QueueRegistration): RegistrationHandlerResult {
        val registrationId = command.memberRegistrationRequest.registrationId
        val outputCommand = try {
            if (command.numberOfRetriesSoFar < MAX_RETRIES) {
                queueRequest(key, command, registrationId)
            } else {
                logger.warn(
                    "Max re-tries exceeded for registration with ID `$registrationId`." +
                            " Registration is discarded."
                )
                emptyList()
            }
        } catch (ex: Exception) {
            logger.warn("Exception happened while queueing the request with ID `$registrationId`. Will re-try again.")
            increaseNumberOfRetries(key, command)
        }

        return RegistrationHandlerResult(state, outputCommand)
    }

    private fun QueueRegistration.toRegistrationRequest(): RegistrationRequest {
        return RegistrationRequest(
            RegistrationStatus.RECEIVED_BY_MGM,
            memberRegistrationRequest.registrationId,
            member.toCorda(),
            memberRegistrationRequest.memberContext,
            memberRegistrationRequest.registrationContext,
            memberRegistrationRequest.serial,
        )
    }

    private fun queueRequest(
        key: String, command: QueueRegistration, registrationId: String
    ): List<Record<String, RegistrationCommand>> {
        logger.info(
            "MGM queueing registration request for ${command.member.x500Name} from group `${command.member.groupId}` " +
                    "with request ID `$registrationId`."
        )
        membershipPersistenceClient.persistRegistrationRequest(
            command.mgm.toCorda(),
            command.toRegistrationRequest()
        ).getOrThrow()
        logger.info(
            "MGM put registration request for ${command.member.x500Name} from group `${command.member.groupId}` " +
                    "with request ID `$registrationId` into the queue."
        )
        return listOf(
            Record(
                REGISTRATION_COMMAND_TOPIC,
                key,
                RegistrationCommand(CheckForPendingRegistration(command.mgm, command.member, 0))
            )
        )
    }

    private fun increaseNumberOfRetries(key: String, command: QueueRegistration) = listOf(
        Record(
            REGISTRATION_COMMAND_TOPIC,
            key,
            RegistrationCommand(
                QueueRegistration(
                    command.mgm,
                    command.member,
                    command.memberRegistrationRequest,
                    command.numberOfRetriesSoFar + 1
                )
            )
        )
    )
}