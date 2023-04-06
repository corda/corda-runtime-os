package net.corda.membership.service.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.MembershipAsyncRequestState
import net.corda.data.membership.async.request.RetriableFailure
import net.corda.data.membership.async.request.SentToMgmWaitingForNetwork
import net.corda.data.membership.common.RegistrationStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.toMap
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.RegistrationProxy
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.WAIT_FOR_MGM_SESSION
import net.corda.utilities.time.Clock
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.util.UUID
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
internal class MemberOpsAsyncProcessor(
    private val registrationProxy: RegistrationProxy,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipQueryClient: MembershipQueryClient,
    private val membershipConfig: SmartConfig,
    private val clock: Clock,
) : StateAndEventProcessor<String, MembershipAsyncRequestState, MembershipAsyncRequest> {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MAX_RETRIES = 10
    }

    private val waitForMgmSeconds by lazy {
        TimeUnit.MINUTES.toSeconds(membershipConfig.getLong("$TTLS.$WAIT_FOR_MGM_SESSION"))
    }

    override fun onNext(
        state: MembershipAsyncRequestState?,
        event: Record<String, MembershipAsyncRequest>,
    ): StateAndEventProcessor.Response<MembershipAsyncRequestState> {
        val value = event.value
        if (value?.request == null) {
            return StateAndEventProcessor.Response(
                updatedState = null,
                responseEvents = emptyList(),
                markForDLQ = true,
            )
        }
        return register(value, state)
    }

    override val keyClass = String::class.java
    override val eventValueClass = MembershipAsyncRequest::class.java
    override val stateValueClass = MembershipAsyncRequestState::class.java

    private fun createFailureWithoutRetryResponse(
        responseEvents: Collection<Record<*, *>>,
    ): StateAndEventProcessor.Response<MembershipAsyncRequestState> =
        StateAndEventProcessor.Response(
            updatedState = null,
            responseEvents = responseEvents.toList(),
            markForDLQ = true,
        )
    private fun persistAndCreateFailureWithoutRetryResponse(
        holdingIdentity: HoldingIdentity,
        registrationId: UUID,
        message: String?,
        status: RegistrationStatus,
    ): StateAndEventProcessor.Response<MembershipAsyncRequestState> {
        val records = membershipPersistenceClient.setRegistrationRequestStatus(
            holdingIdentity,
            registrationId.toString(),
            status,
            message?.take(255),
        ).createAsyncCommands()
        return createFailureWithoutRetryResponse(records)
    }

    private fun createMoveOnResponse(): StateAndEventProcessor.Response<MembershipAsyncRequestState> =
        StateAndEventProcessor.Response(
            updatedState = null,
            responseEvents = emptyList(),
            markForDLQ = false,
        )

    private fun createFailureWithRetryResponse(
        value: MembershipAsyncRequest,
        retries: Int,
    ): StateAndEventProcessor.Response<MembershipAsyncRequestState> {
        return StateAndEventProcessor.Response(
            updatedState = MembershipAsyncRequestState(
                value,
                RetriableFailure(retries - 1),
            ),
            responseEvents = emptyList(),
            markForDLQ = false,
        )
    }

    private fun createSentToMgmResponse(
        value: MembershipAsyncRequest,
        state: MembershipAsyncRequestState?,
        records: Collection<Record<*, *>>,
    ): StateAndEventProcessor.Response<MembershipAsyncRequestState> {
        val currentCause = state?.cause
        val newCause = if (currentCause is SentToMgmWaitingForNetwork) {
            currentCause
        } else {
            SentToMgmWaitingForNetwork(
                clock.instant().plusSeconds(waitForMgmSeconds),
            )
        }
        return StateAndEventProcessor.Response(
            updatedState = MembershipAsyncRequestState(
                value,
                newCause,
            ),
            responseEvents = records.toList(),
            markForDLQ = false,
        )
    }

    private fun MembershipAsyncRequestState?.retries(): Int {
        return (this?.cause as? RetriableFailure)?.numberOfRemainingRetries ?: MAX_RETRIES
    }

    private fun register(
        value: MembershipAsyncRequest,
        state: MembershipAsyncRequestState?,
    ): StateAndEventProcessor.Response<MembershipAsyncRequestState> {
        val request = value.request
        val registrationId = try {
            UUID.fromString(request.requestId)
        } catch (e: IllegalArgumentException) {
            logger.warn("Registration ${request.requestId} failed. Invalid request ID.", e)
            return createFailureWithoutRetryResponse(
                emptyList(),
            )
        }
        val holdingIdentityShortHash = ShortHash.of(request.holdingIdentityId)
        val holdingIdentity =
            virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)?.holdingIdentity
        if (holdingIdentity == null) {
            val retries = state.retries()
            return if (retries <= 0) {
                logger.warn(
                    "Registration ${request.requestId} failed." +
                        " Could not find holding identity associated with ${request.holdingIdentityId} for too long." +
                        " Will not retry nor persist the state.",
                )
                createFailureWithoutRetryResponse(
                    emptyList(),
                )
            } else {
                logger.warn(
                    "Registration ${request.requestId} failed." +
                        " Could not find holding identity associated with ${request.holdingIdentityId}",
                )
                createFailureWithRetryResponse(value, retries)
            }
        }
        val cause = state?.cause
        if (cause is SentToMgmWaitingForNetwork) {
            if (cause.stopRetriesAfter.isBefore(clock.instant())) {
                val message = "Registration request ${state.request.request} had been sent to the MGM too long"
                logger.warn(
                    "Registration request ${state.request.request} had been sent to the MGM too long ago and no " +
                        "response had been received. Will not retry it.",
                )
                return persistAndCreateFailureWithoutRetryResponse(
                    holdingIdentity,
                    registrationId,
                    message,
                    RegistrationStatus.FAILED,
                )
            }
        }
        return try {
            val requestStatus = membershipQueryClient.queryRegistrationRequest(
                holdingIdentity,
                request.requestId,
            ).getOrThrow()
            if (cause is SentToMgmWaitingForNetwork) {
                if ((requestStatus == null) ||
                    (requestStatus.registrationStatus == RegistrationStatus.NEW) ||
                    (requestStatus.registrationStatus == RegistrationStatus.SENT_TO_MGM)
                ) {
                    logger.info("Request $registrationId had not received any reply from the MGM. Trying again...")
                } else {
                    // This request had already moved on.
                    return createMoveOnResponse()
                }
            } else {
                if ((requestStatus != null) && (requestStatus.registrationStatus != RegistrationStatus.NEW)) {
                    // This request had already passed this state. no need to continue.
                    return createMoveOnResponse()
                }
            }

            logger.info("Processing registration ${request.requestId} to ${holdingIdentity.x500Name}.")
            val records = registrationProxy.register(registrationId, holdingIdentity, request.context.toMap())
            logger.info("Processed registration ${request.requestId} to ${holdingIdentity.x500Name}.")
            createSentToMgmResponse(value, state, records)
        } catch (e: InvalidMembershipRegistrationException) {
            logger.warn("Registration ${request.requestId} failed. Invalid registration request.", e)
            persistAndCreateFailureWithoutRetryResponse(
                holdingIdentity,
                registrationId,
                e.message,
                RegistrationStatus.INVALID,
            )
        } catch (e: Exception) {
            val retries = state.retries()
            if (retries <= 0) {
                logger.warn("Registration ${value.request.requestId} failed too many times. Will not retry again.", e)
                persistAndCreateFailureWithoutRetryResponse(
                    holdingIdentity,
                    registrationId,
                    e.message,
                    RegistrationStatus.FAILED,
                )
            } else {
                logger.warn("Registration ${request.requestId} failed. Will retry soon.", e)
                createFailureWithRetryResponse(value, retries)
            }
        }
    }
}
