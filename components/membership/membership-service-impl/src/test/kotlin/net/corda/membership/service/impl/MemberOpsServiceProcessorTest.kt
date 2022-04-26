package net.corda.membership.service.impl

import net.corda.data.KeyValuePairList
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.request.RegistrationAction
import net.corda.data.membership.rpc.request.RegistrationRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.data.membership.rpc.response.RegistrationResponse
import net.corda.data.membership.rpc.response.RegistrationStatus
import net.corda.libs.packaging.CpiIdentifier
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.RegistrationProxy
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MemberOpsServiceProcessorTest {

    companion object {
        private lateinit var processor: MemberOpsServiceProcessor
        private lateinit var registrationProxy: RegistrationProxy
        private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService

        private val holdingIdentity = HoldingIdentity("test", "0")
        private const val HOLDING_IDENTITY_STRING = "test"

        @JvmStatic
        @BeforeAll
        fun setup() {
            registrationProxy = mock {
                on { register(holdingIdentity) } doReturn (MembershipRequestRegistrationResult(
                    MembershipRequestRegistrationOutcome.SUBMITTED
                ))
            }
            virtualNodeInfoReadService = mock {
                on { getById(HOLDING_IDENTITY_STRING) } doReturn VirtualNodeInfo(
                    holdingIdentity,
                    CpiIdentifier("test", "test", SecureHash("algorithm", "1234".toByteArray())),
                    null, UUID.randomUUID(), null, UUID.randomUUID()
                )
            }
            processor = MemberOpsServiceProcessor(registrationProxy, virtualNodeInfoReadService)
        }
    }

    private fun assertResponseContext(expected: MembershipRpcRequestContext, actual: MembershipRpcResponseContext) {
        assertEquals(expected.requestId, actual.requestId)
        assertEquals(expected.requestTimestamp, actual.requestTimestamp)
        val now = Instant.now()
        assertThat(
            actual.responseTimestamp.epochSecond,
            allOf(greaterThanOrEqualTo(expected.requestTimestamp.epochSecond), lessThanOrEqualTo(now.epochSecond))
        )
    }

    @Test
    fun `should successfully submit registration request`() {
        val requestTimestamp = Instant.now()
        val requestContext = MembershipRpcRequestContext(
            UUID.randomUUID().toString(),
            requestTimestamp
        )
        val request = MembershipRpcRequest(
            requestContext,
            RegistrationRequest(
                HOLDING_IDENTITY_STRING,
                RegistrationAction.REQUEST_JOIN
            )
        )
        val future = CompletableFuture<MembershipRpcResponse>()
        processor.onNext(request, future)
        val result = future.get()
        val expectedResponse = RegistrationResponse(
            requestTimestamp,
            RegistrationStatus.SUBMITTED,
            1,
            KeyValuePairList(emptyList()),
            KeyValuePairList(emptyList())
        )
        assertEquals(expectedResponse, result.response)
        assertResponseContext(requestContext, result.responseContext)
    }

    @Test
    fun `should fail in case of unknown request`() {
        val request = MembershipRpcRequest(
            MembershipRpcRequestContext(
                UUID.randomUUID().toString(),
                Instant.now()
            ),
            mock()
        )
        val future = CompletableFuture<MembershipRpcResponse>()
        processor.onNext(request, future)
        val exception = assertThrows<ExecutionException> {
            future.get()
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause, instanceOf(MembershipRegistrationException::class.java))
    }
}
