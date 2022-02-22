package net.corda.membership.impl.httprpc

import net.corda.data.KeyValuePairList
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.request.RegistrationAction
import net.corda.data.membership.rpc.request.RegistrationRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.data.membership.rpc.response.RegistrationResponse
import net.corda.data.membership.rpc.response.RegistrationStatus
import net.corda.membership.MembershipRegistrationException
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.provider.RegistrationProvider
import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.hamcrest.CoreMatchers
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

class MembershipRpcOpsProcessorTest {

    companion object {
        private lateinit var processor: MembershipRpcOpsProcessor
        private lateinit var registrationProvider: RegistrationProvider
        private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService

        private val holdingIdentity = HoldingIdentity("test", "0")
        private const val HOLDING_IDENTITY_STRING = "test"

        @JvmStatic
        @BeforeAll
        fun setup() {
            registrationProvider = mock {
                val service: MemberRegistrationService = mock {
                    on { register(holdingIdentity) } doReturn (MembershipRequestRegistrationResult(
                        MembershipRequestRegistrationOutcome.SUBMITTED
                    ))
                }
                on { get(holdingIdentity) } doReturn (service)
            }
            virtualNodeInfoReadService = mock {
                on { getById(HOLDING_IDENTITY_STRING) } doReturn VirtualNodeInfo(
                    holdingIdentity,
                    CPI.Identifier.newInstance("test", "test", SecureHash("algorithm", "1234".toByteArray()))
                )
            }
            processor = MembershipRpcOpsProcessor(registrationProvider, virtualNodeInfoReadService)
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
            KeyValuePairList(),
            KeyValuePairList()
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
        assertThat(exception.cause, CoreMatchers.instanceOf(MembershipRegistrationException::class.java))
    }
}
