package net.corda.membership.impl.persistence.client

import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_DB_ASYNC_TOPIC
import net.corda.utilities.Either
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * An implementation of the MembershipPersistenceOperation.
 *
 * @param sender An RPC sender to enable the send request.
 * @param request The persistence request (to be sent either via the RPC sender or to
 *   create the asynchronous command from).
 * @param convertResult A function to convert the result from the
 *   MembershipPersistenceResponse payload to either a successful
 *   result (Either.Left) or a failure message (Either.Right)
 */
internal class MembershipPersistenceOperationImpl<T>(
    private val sender: RPCSender<MembershipPersistenceRequest, MembershipPersistenceResponse>?,
    private val request: MembershipPersistenceRequest,
    private val convertResult: (Any?) -> Either<T, String>,
) : MembershipPersistenceOperation<T> {
    private companion object {
        const val RPC_TIMEOUT_MS = 10000L
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun send(): Either<T, MembershipPersistenceResult.Failure<T>> {
        if (sender == null) {
            val failureReason = "Persistence client could not send persistence request because the RPC sender has not been initialised."
            logger.warn(failureReason)
            return Either.Right(MembershipPersistenceResult.Failure(failureReason))
        }
        val requestId = request.context.requestId
        logger.debug { "Sending membership persistence RPC request ID: $requestId." }
        return try {
            val response = sender
                .sendRequest(request)
                .getOrThrow(Duration.ofMillis(RPC_TIMEOUT_MS))
            val payload = response.payload
            val context = response.context
            if (context.holdingIdentity != request.context.holdingIdentity) {
                Either.Right(
                    MembershipPersistenceResult.Failure(
                        "Holding identity in the response received does not match what was sent in the request.",
                    ),
                )
            } else if (context.requestTimestamp != request.context.requestTimestamp) {
                Either.Right(
                    MembershipPersistenceResult.Failure(
                        "Request timestamp in the response received does not match what was sent in the request.",
                    ),
                )
            } else if (context.requestId != request.context.requestId) {
                Either.Right(
                    MembershipPersistenceResult.Failure(
                        "Request ID in the response received does not match what was sent in the request.",
                    ),
                )
            } else if (context.requestTimestamp > response.context.responseTimestamp) {
                Either.Right(
                    MembershipPersistenceResult.Failure(
                        "Response timestamp is before the request timestamp.",
                    ),
                )
            } else if (payload is PersistenceFailedResponse) {
                Either.Right(
                    MembershipPersistenceResult.Failure(payload.errorMessage, payload.errorKind),
                )
            } else {
                convertResult(payload).mapRight {
                    MembershipPersistenceResult.Failure(it)
                }
            }
        } catch (e: IllegalArgumentException) {
            Either.Right(
                MembershipPersistenceResult.Failure(
                    "Invalid response for request $requestId. ${e.message}",
                ),
            )
        } catch (e: TimeoutException) {
            Either.Right(
                MembershipPersistenceResult.Failure(
                    "Timeout waiting for response from membership persistence RPC request ID: $requestId.",
                ),
            )
        } catch (e: Exception) {
            Either.Right(
                MembershipPersistenceResult.Failure(
                    "Exception occurred while sending RPC request ID: $requestId. ${e.message}",
                ),
            )
        }
    }

    override fun execute(): MembershipPersistenceResult<T> {
        return when (val result = send()) {
            is Either.Left -> MembershipPersistenceResult.Success(result.a)
            is Either.Right -> result.b
        }
    }

    override fun createAsyncCommands(): Collection<Record<*, *>> = listOf(
        Record(
            topic = MEMBERSHIP_DB_ASYNC_TOPIC,
            key = request.context.requestId,
            value = MembershipPersistenceAsyncRequest(request),
        ),
    ).also {
        logger.info("Sending async membership persistence RPC request ID: ${request.context.requestId}.")
    }
}
