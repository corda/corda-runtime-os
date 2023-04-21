package net.corda.membership.impl.persistence.client

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.publisher.RPCSender
import net.corda.schema.Schemas.Membership.MEMBERSHIP_DB_ASYNC_TOPIC
import net.corda.utilities.Either
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

class MembershipPersistenceOperationImplTest {
    private val requestContext = MembershipRequestContext(
        Instant.ofEpochMilli(1002),
        "requestId",
        HoldingIdentity(
            "name",
            "group",
        )
    )
    private val request = MembershipPersistenceRequest(requestContext, 50)
    private val responseContext = MembershipResponseContext(
        Instant.ofEpochMilli(1002),
        "requestId",
        Instant.ofEpochMilli(1030),
        HoldingIdentity(
            "name",
            "group",
        )
    )
    private val response = MembershipPersistenceResponse(
        responseContext,
        100
    )
    private val sender = mock<RPCSender<MembershipPersistenceRequest, MembershipPersistenceResponse>> {
        on { sendRequest(any()) } doReturn CompletableFuture.completedFuture(response)
    }
    private val operation = MembershipPersistenceOperationImpl(
        sender,
        request
    ) {
        Either.Left(5)
    }

    @Nested
    inner class SentTests {
        @Test
        fun `it will invoke the request`() {
            operation.send()

            verify(sender).sendRequest(request)
        }

        @Test
        fun `it will give the correct reply`() {
            val reply = operation.send()

            assertThat(reply).isEqualTo(Either.Left(5))
        }

        @Test
        fun `timeout will give the correct error`() {
            val future = mock<CompletableFuture<MembershipPersistenceResponse>> {
                on { get(any(), any()) } doThrow TimeoutException("")
            }
            whenever(sender.sendRequest(any())).doReturn(future)

            val reply = operation.send()

            assertThat((reply as? Either.Right)?.b).contains("Timeout")
        }

        @Test
        fun `null sender will return an error`() {
            val operation = MembershipPersistenceOperationImpl(
                null,
                request,
            ) {
                Either.Left(5)
            }

            val reply = operation.send()

            assertThat(reply).isInstanceOf(Either.Right::class.java)
        }

        @Test
        fun `wrong holding identity will give the correct error`() {
            responseContext.holdingIdentity = HoldingIdentity(
                "another name",
                "group",
            )

            val reply = operation.send()

            assertThat((reply as? Either.Right)?.b).contains("Holding identity in the response")
        }

        @Test
        fun `wrong timestamp will give the correct error`() {
            responseContext.requestTimestamp = Instant.ofEpochMilli(4000)

            val reply = operation.send()

            assertThat((reply as? Either.Right)?.b).contains("Request timestamp in the response")
        }

        @Test
        fun `wrong request ID will give the correct error`() {
            responseContext.requestId = "nop"

            val reply = operation.send()

            assertThat((reply as? Either.Right)?.b).contains("Request ID in the response")
        }

        @Test
        fun `early response timestamp will give error`() {
            responseContext.responseTimestamp = requestContext.requestTimestamp.minusMillis(1000)

            val reply = operation.send()

            assertThat((reply as? Either.Right)?.b).contains("Response timestamp is before")
        }

        @Test
        fun `PersistenceFailedResponse as reply will give the correct error`() {
            response.payload = PersistenceFailedResponse("oops")

            val reply = operation.send()

            assertThat((reply as? Either.Right)?.b).contains("oops")
        }

        @Test
        fun `it will call the convert with the correct payload`() {
            var payload: Any? = null
            val operation = MembershipPersistenceOperationImpl(
                sender,
                request,
            ) {
                payload = it
                Either.Left(43)
            }

            operation.send()

            assertThat(payload).isEqualTo(100)
        }

        @Test
        fun `it will catch IllegalArgumentException`() {
            val operation = MembershipPersistenceOperationImpl<Int>(
                sender,
                request,
            ) {
                throw IllegalArgumentException("nop")
            }

            val reply = operation.send()

            assertThat((reply as? Either.Right)?.b)
                .contains("Invalid response for request")
                .contains("nop")
        }

        @Test
        fun `it will catch other exception`() {
            val operation = MembershipPersistenceOperationImpl<Int>(
                sender,
                request,
            ) {
                throw CordaRuntimeException("nop")
            }

            val reply = operation.send()

            assertThat((reply as? Either.Right)?.b)
                .contains("Exception occurred")
                .contains("nop")
        }
    }

    @Nested
    inner class ExecuteTests {
        @Test
        fun `it will return success if the request was successful`() {
            val reply = operation.execute()

            assertThat(reply).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        }

        @Test
        fun `it will return error if the request had failed`() {
            response.payload = PersistenceFailedResponse("oops")

            val reply = operation.execute()

            assertThat(reply).isInstanceOf(MembershipPersistenceResult.Failure::class.java)
        }
    }

    @Test
    fun `createAsyncCommands will return the correct commands`() {
        val commands = operation.createAsyncCommands()

        assertThat(commands)
            .anySatisfy {
                assertThat(it.topic).isEqualTo(MEMBERSHIP_DB_ASYNC_TOPIC)
                assertThat(it.key).isEqualTo(requestContext.requestId)
                val value = it.value as? MembershipPersistenceAsyncRequest
                assertThat(value?.request).isEqualTo(request)
            }
            .hasSize(1)
    }
}
