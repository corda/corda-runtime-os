package net.corda.membership.service.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.MembershipAsyncRequestState
import net.corda.data.membership.async.request.RegistrationAsyncRequest
import net.corda.data.membership.async.request.RetryCondition
import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.lib.toMap
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.RegistrationProxy
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.util.UUID

internal class MemberOpsAsyncProcessor(
    private val registrationProxy: RegistrationProxy,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipQueryClient: MembershipQueryClient,
    private val clock: Clock,
) : StateAndEventProcessor<String, MembershipAsyncRequestState, MembershipAsyncRequest> {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MAX_RETRIES = 10
        const val WAIT_BETWEEN_REQUESTS_AFTER_FAILURE_IN_SECONDS = 2L
        const val WAIT_TO_P2P_IN_SECONDS = 40L
    }

    private enum class Outcome {
        SUCCESS_NO_CONDITION,
        SUCCESS_WITH_CONDITION,
        FAILED_CANNOT_RETRY,
        FAILED_CAN_RETRY,
    }

    override fun onNext(
        state: MembershipAsyncRequestState?,
        event: Record<String, MembershipAsyncRequest>,
    ): StateAndEventProcessor.Response<MembershipAsyncRequestState> {
        val numberOfRetriesSoFar = state?.numberOfRetriesSoFar ?: 0
        val canRetry = numberOfRetriesSoFar < MAX_RETRIES
        val condition = state?.retryCondition
        val outcome = when (val request = event.value?.request) {
            is RegistrationAsyncRequest -> {
                register(request, canRetry, condition)
            }
            else -> {
                logger.warn("Can not handle: $request")
                Outcome.FAILED_CANNOT_RETRY
            }
        }

        return when (outcome) {
            Outcome.SUCCESS_NO_CONDITION -> StateAndEventProcessor.Response(
                updatedState = null,
                responseEvents = emptyList(),
                markForDLQ = false,
            )
            Outcome.FAILED_CANNOT_RETRY -> StateAndEventProcessor.Response(
                updatedState = null,
                responseEvents = emptyList(),
                markForDLQ = true,
            )
            Outcome.FAILED_CAN_RETRY -> StateAndEventProcessor.Response(
                updatedState = MembershipAsyncRequestState(
                    event.value,
                    numberOfRetriesSoFar + 1,
                    RetryCondition.NO_CONDITION,
                    clock.instant().plusSeconds(WAIT_BETWEEN_REQUESTS_AFTER_FAILURE_IN_SECONDS),
                ),
                markForDLQ = false,
                responseEvents = emptyList(),
            )
            Outcome.SUCCESS_WITH_CONDITION -> StateAndEventProcessor.Response(
                updatedState = MembershipAsyncRequestState(
                    event.value,
                    numberOfRetriesSoFar + 1,
                    RetryCondition.IS_STATE_SENT_TO_MGM,
                    clock.instant().plusSeconds(WAIT_TO_P2P_IN_SECONDS),
                ),
                markForDLQ = false,
                responseEvents = emptyList(),
            )
        }
    }

    override val keyClass = String::class.java
    override val eventValueClass = MembershipAsyncRequest::class.java
    override val stateValueClass = MembershipAsyncRequestState::class.java

    private fun register(
        request: RegistrationAsyncRequest,
        canRetry: Boolean,
        condition: RetryCondition?
    ): Outcome {
        val holdingIdentityShortHash = ShortHash.of(request.holdingIdentityId)
        val holdingIdentity =
            virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)?.holdingIdentity
        if (holdingIdentity == null) {
            logger.warn(
                "Registration ${request.requestId} failed." +
                    " Could not find holding identity associated with ${request.holdingIdentityId}"
            )
            return Outcome.FAILED_CAN_RETRY
        }
        val registrationId = try {
            UUID.fromString(request.requestId)
        } catch (e: IllegalArgumentException) {
            logger.warn("Registration ${request.requestId} failed. Invalid request ID.", e)
            return Outcome.FAILED_CANNOT_RETRY
        }
        return try {
            val requestStatus = membershipQueryClient.queryRegistrationRequest(
                holdingIdentity,
                request.requestId
            ).getOrThrow()
            if (condition == RetryCondition.IS_STATE_SENT_TO_MGM) {
                if ((requestStatus == null) ||
                    (requestStatus.registrationStatus == RegistrationStatus.NEW) ||
                    (requestStatus.registrationStatus == RegistrationStatus.SENT_TO_MGM)
                ) {
                    logger.info("Request $registrationId had not received any reply from the MGM. Trying again...")
                } else {
                    // This request had already moved on.
                    return Outcome.SUCCESS_NO_CONDITION
                }
            } else {
                if ((requestStatus != null) && (requestStatus.registrationStatus != RegistrationStatus.NEW)) {
                    // This request had already passed this state. no need to continue.
                    return Outcome.SUCCESS_NO_CONDITION
                }
            }

            logger.info("Processing registration ${request.requestId} to ${holdingIdentity.x500Name}.")
            // CORE-10367: return the status update command as part of the onNext
            registrationProxy.register(registrationId, holdingIdentity, request.context.toMap())
            logger.info("Processed registration ${request.requestId} to ${holdingIdentity.x500Name}.")
            Outcome.SUCCESS_WITH_CONDITION
        } catch (e: InvalidMembershipRegistrationException) {
            // CORE-10367: return the status update command as part of the onNext
            membershipPersistenceClient.setRegistrationRequestStatus(
                holdingIdentity,
                registrationId.toString(),
                RegistrationStatus.INVALID,
                e.message?.take(255),
            )
            logger.warn("Registration ${request.requestId} failed. Invalid registration request.", e)
            Outcome.FAILED_CANNOT_RETRY
        } catch (e: Exception) {
            if (canRetry) {
                logger.warn("Registration ${request.requestId} failed. Will retry soon.", e)
                Outcome.FAILED_CAN_RETRY
            } else {
                // CORE-10367: return the status update command as part of the onNext
                membershipPersistenceClient.setRegistrationRequestStatus(
                    holdingIdentity,
                    registrationId.toString(),
                    RegistrationStatus.INVALID,
                    e.message?.take(255),
                )
                logger.warn("Registration ${request.requestId} failed too many times. Will not retry again", e)
                Outcome.FAILED_CANNOT_RETRY
            }
        }
    }
}
