package net.corda.membership.service.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.MembershipAsyncRequestState
import net.corda.data.membership.async.request.RetriableFailure
import net.corda.data.membership.async.request.SentToMgmWaitingForNetwork
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.registration.RegistrationStatusExt.order
import net.corda.membership.lib.toMap
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.RegistrationProxy
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ASYNC_REQUEST_RETRIES_TOPIC
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.WAIT_FOR_MGM_SESSION
import net.corda.utilities.Either
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
) : DurableProcessor<String, MembershipAsyncRequest> {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MAX_RETRIES = 10
        const val WAIT_AFTER_FAILURE_IN_SECONDS = 10L
    }

    private val waitForMgmSeconds by lazy {
        TimeUnit.MINUTES.toSeconds(membershipConfig.getLong("$TTLS.$WAIT_FOR_MGM_SESSION"))
    }

    override fun onNext(
        events: List<Record<String, MembershipAsyncRequest>>,
    ): List<Record<*, *>> {
        return events.mapNotNull {
            it.value
        }
            .flatMap {
                handleRequest(it)
            }
    }

    private fun handleRequest(request: MembershipAsyncRequest): Collection<Record<*, *>> {
        return register(request)
    }

    override val keyClass = String::class.java
    override val valueClass = MembershipAsyncRequest::class.java

    private fun createFailureWithoutRetryResponse(
        requestId: String,
        responseEvents: Collection<Record<*, *>>,
    ): Collection<Record<*, *>> =
        responseEvents + Record(
            MEMBERSHIP_ASYNC_REQUEST_RETRIES_TOPIC,
            requestId,
            null,
        )

    private fun persistAndCreateFailureWithoutRetryResponse(
        holdingIdentity: HoldingIdentity,
        registrationId: UUID,
        message: String?,
        status: RegistrationStatus,
    ): Collection<Record<*, *>> {
        val records = membershipPersistenceClient.setRegistrationRequestStatus(
            holdingIdentity,
            registrationId.toString(),
            status,
            message,
        ).createAsyncCommands()
        return createFailureWithoutRetryResponse(registrationId.toString(), records)
    }

    private fun createMoveOnResponse(
        requestId: String,
    ): Collection<Record<*, *>> =
        listOf(
            Record(
                MEMBERSHIP_ASYNC_REQUEST_RETRIES_TOPIC,
                requestId,
                null,
            ),
        )

    private fun createFailureWithRetryResponse(
        requestId: String,
        value: MembershipAsyncRequest,
        retries: Int,
    ): Collection<Record<*, *>> {
        return listOf(
            Record(
                MEMBERSHIP_ASYNC_REQUEST_RETRIES_TOPIC,
                requestId,
                MembershipAsyncRequestState(
                    value.request,
                    RetriableFailure(retries - 1, clock.instant().plusSeconds(WAIT_AFTER_FAILURE_IN_SECONDS)),
                ),
            ),
        )
    }

    private fun createSentToMgmResponse(
        value: MembershipAsyncRequest,
        state: MembershipAsyncRequestState?,
        records: Collection<Record<*, *>>,
    ): Collection<Record<*, *>> {
        val currentCause = state?.cause
        val newCause = if (currentCause is SentToMgmWaitingForNetwork) {
            currentCause
        } else {
            SentToMgmWaitingForNetwork(
                clock.instant().plusSeconds(waitForMgmSeconds),
            )
        }
        return records +
            Record(
                MEMBERSHIP_ASYNC_REQUEST_RETRIES_TOPIC,
                value.request.requestId,
                MembershipAsyncRequestState(
                    value.request,
                    newCause,
                ),
            )
    }

    private fun MembershipAsyncRequestState?.retries(): Int {
        return (this?.cause as? RetriableFailure)?.numberOfRemainingRetries ?: MAX_RETRIES
    }

    private fun getRegistrationIdAndHoldingId(
        value: MembershipAsyncRequest,
    ): Either<Pair<UUID, HoldingIdentity>, Collection<Record<*, *>>> {
        val request = value.request
        val registrationId = try {
            UUID.fromString(request.requestId)
        } catch (e: IllegalArgumentException) {
            logger.warn("Registration ${request.requestId} failed. Invalid request ID.", e)
            return Either.Right(
                createFailureWithoutRetryResponse(
                    request.requestId,
                    emptyList(),
                ),
            )
        }
        val holdingIdentityShortHash = ShortHash.of(request.holdingIdentityId)
        val holdingIdentity =
            virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)?.holdingIdentity
        return if (holdingIdentity != null) {
            Either.Left(registrationId to holdingIdentity)
        } else {
            val retries = value.state.retries()
            val response = if (retries <= 0) {
                logger.warn(
                    " Could not find holding identity associated with ${request.holdingIdentityId} for too long." +
                        " Request will not be retried further.",
                )
                createFailureWithoutRetryResponse(
                    request.requestId,
                    emptyList(),
                )
            } else {
                logger.warn(
                    "Could not find holding identity associated with ${request.holdingIdentityId}. " +
                        "Request ${request.requestId} will be retried later.",
                )
                createFailureWithRetryResponse(request.requestId, value, retries)
            }
            Either.Right(response)
        }
    }

    private fun waitingFromMgmForTooLong(
        holdingIdentity: HoldingIdentity,
        registrationId: UUID,
        state: MembershipAsyncRequestState?,
    ): Collection<Record<*, *>>? {
        val cause = state?.cause
        if (cause is SentToMgmWaitingForNetwork) {
            if (cause.stopRetriesAfter.isBefore(clock.instant())) {
                val message = "Registration request was not acknowledged as received by the MGM after many attempts to send it."
                logger.warn(
                    "Registration request ${state.request.requestId} was not acknowledged as received by the MGM " +
                        "after many attempts to send it. No more retries will be attempted and the request will be marked as FAILED.",
                )
                return persistAndCreateFailureWithoutRetryResponse(
                    holdingIdentity,
                    registrationId,
                    message,
                    RegistrationStatus.FAILED,
                )
            }
        }
        return null
    }

    private fun checkIfRegistrationPassedInitialState(
        holdingIdentity: HoldingIdentity,
        registrationId: UUID,
        state: MembershipAsyncRequestState?,
    ): Collection<Record<*, *>>? {
        val cause = state?.cause
        val requestStatus = membershipQueryClient.queryRegistrationRequest(
            holdingIdentity,
            registrationId.toString(),
        ).getOrThrow()
        if (cause is SentToMgmWaitingForNetwork) {
            if ((requestStatus == null) ||
                (requestStatus.registrationStatus.order <= RegistrationStatus.SENT_TO_MGM.order)
            ) {
                logger.info("Request $registrationId had not received any reply from the MGM. Trying again...")
            } else {
                // This request had already moved on.
                return createMoveOnResponse(registrationId.toString())
            }
        } else {
            if ((requestStatus != null) && (requestStatus.registrationStatus.order > RegistrationStatus.NEW.order)) {
                // This request had already passed this state. no need to continue.
                return createMoveOnResponse(registrationId.toString())
            }
        }

        return null
    }

    private fun register(
        value: MembershipAsyncRequest,
    ): Collection<Record<*, *>> {
        val (registrationId, holdingIdentity) = when (val reply = getRegistrationIdAndHoldingId(value)) {
            is Either.Left -> reply.a
            is Either.Right -> return reply.b
        }
        waitingFromMgmForTooLong(holdingIdentity, registrationId, value.state)?.let {
            return@register it
        }
        return try {
            checkIfRegistrationPassedInitialState(holdingIdentity, registrationId, value.state)?.let {
                return@register it
            }

            logger.info("Processing registration $registrationId to ${holdingIdentity.x500Name}.")
            val records = registrationProxy.register(
                registrationId,
                holdingIdentity,
                value.request.context.toMap(),
            )
            logger.info("Processed registration $registrationId to ${holdingIdentity.x500Name}.")
            createSentToMgmResponse(value, value.state, records)
        } catch (e: InvalidMembershipRegistrationException) {
            logger.warn("Registration $registrationId failed. Invalid registration request.", e)
            persistAndCreateFailureWithoutRetryResponse(
                holdingIdentity,
                registrationId,
                e.message,
                RegistrationStatus.INVALID,
            )
        } catch (e: Exception) {
            val retries = value.state.retries()
            if (retries <= 0) {
                logger.warn("Registration ${value.request.requestId} failed too many times. Will not retry again.", e)
                persistAndCreateFailureWithoutRetryResponse(
                    holdingIdentity,
                    registrationId,
                    e.message,
                    RegistrationStatus.FAILED,
                )
            } else {
                logger.warn("Registration $registrationId failed. Will retry soon.", e)
                createFailureWithRetryResponse(registrationId.toString(), value, retries)
            }
        }
    }
}
