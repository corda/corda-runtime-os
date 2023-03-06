package net.corda.membership.impl.persistence.client

import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.membership.lib.Either
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_DB_ASYNC_TOPIC
import net.corda.utilities.concurrent.getOrThrow
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeoutException

class MembershipPersistenceOperationImpl<T>(
    private val sender: RPCSender<MembershipPersistenceRequest, MembershipPersistenceResponse>?,
    private val request: MembershipPersistenceRequest,
    private val convertResult: (Any?) -> Either<T, String>,
) : MembershipPersistenceOperation<T> {
    private companion object {
        const val RPC_TIMEOUT_MS = 10000L
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun send(): Either<T, String> {
        if (sender == null) {
            val failureReason = "Persistence client could not send persistence request because the RPC sender has not been initialised."
            logger.warn(failureReason)
            return Either.Right(failureReason)
        }
        val requestId = request.context.requestId
        logger.info("Sending membership persistence RPC request ID: $requestId.")
        return try {
            val response = sender
                .sendRequest(request)
                .getOrThrow(Duration.ofMillis(RPC_TIMEOUT_MS))
            val payload = response.payload
            val context = response.context
            if (context.holdingIdentity != request.context.holdingIdentity) {
                Either.Right("Holding identity in the response received does not match what was sent in the request.")
            } else if (context.requestTimestamp != request.context.requestTimestamp) {
                Either.Right("Request timestamp in the response received does not match what was sent in the request.")
            } else if (context.requestId != request.context.requestId) {
                Either.Right("Request ID in the response received does not match what was sent in the request.")
            } else if (context.requestTimestamp > response.context.responseTimestamp) {
                Either.Right("Response timestamp is before the request timestamp.")
            } else if (payload is PersistenceFailedResponse) {
                Either.Right(payload.errorMessage)
            } else {
                convertResult(payload)
            }
        } catch (e: IllegalArgumentException) {
            Either.Right("Invalid response for request $requestId. ${e.message}")
        } catch (e: TimeoutException) {
            Either.Right("Timeout waiting for response from membership persistence RPC request ID: $requestId.")
        } catch (e: Exception) {
            Either.Right("Exception occurred while sending RPC request ID: $requestId. ${e.message}")
        }
    }

    override fun execute(): MembershipPersistenceResult<T> {
        return when (val result = send()) {
            is Either.Left -> MembershipPersistenceResult.Success(result.a)
            is Either.Right -> MembershipPersistenceResult.Failure(result.b)
        }
    }

    override fun createAsyncCommands(): Collection<Record<*, *>> = listOf(
        Record(
            topic = MEMBERSHIP_DB_ASYNC_TOPIC,
            key = request.context.requestId,
            value = MembershipPersistenceAsyncRequest(request),
        )
    )
}
