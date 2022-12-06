package net.corda.membership.service.impl

import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.rpc.request.MGMGroupPolicyRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.request.RegistrationRpcAction
import net.corda.data.membership.rpc.request.RegistrationRpcRequest
import net.corda.data.membership.rpc.request.RegistrationStatusRpcRequest
import net.corda.data.membership.rpc.request.RegistrationStatusSpecificRpcRequest
import net.corda.data.membership.rpc.response.MGMGroupPolicyResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.data.membership.rpc.response.RegistrationRpcResponse
import net.corda.data.membership.rpc.response.RegistrationRpcStatus
import net.corda.data.membership.rpc.response.RegistrationStatusResponse
import net.corda.data.membership.rpc.response.RegistrationsStatusResponse
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.PROTOCOL_MODE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.SESSION_KEY_POLICY
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.CIPHER_SUITE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.MGM_INFO
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.P2P_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.REGISTRATION_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PropertyKeys
import net.corda.membership.lib.impl.MGMContextImpl
import net.corda.membership.lib.impl.MemberContextImpl
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.RegistrationProxy
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MemberOpsServiceProcessorTest {

    companion object {
        val mgmX500Name = MemberX500Name.parse("O=MGM,L=London,C=GB")
        private const val MGM_GROUP_ID = "090ae2ea-3920-42d7-a5cf-a79b909d7c30"
        private val mgmHoldingIdentity = HoldingIdentity(
            mgmX500Name,
            MGM_GROUP_ID
        )

        private val now = Instant.ofEpochSecond(300)
        private val clock = TestClock(now)
    }

    private var registrationProxy: RegistrationProxy = mock {
        on { register(any(), eq(mgmHoldingIdentity), any()) } doReturn (MembershipRequestRegistrationResult(
            MembershipRequestRegistrationOutcome.SUBMITTED
        ))
    }

    private var virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(mgmHoldingIdentity.shortHash) } doReturn VirtualNodeInfo(
            mgmHoldingIdentity,
            CpiIdentifier("test", "test", SecureHash("algorithm", "1234".toByteArray())),
            null,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            timestamp = now
        )
    }

    private val mgmMemberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
        mapOf(
            GROUP_ID to MGM_GROUP_ID
        )
    )
    private val mgmMgmContext = LayeredPropertyMapMocks.create<MGMContextImpl>(
        mapOf(
            IS_MGM to "true"
        )
    )
    private val mgmMemberInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn mgmMemberContext
        on { mgmProvidedContext } doReturn mgmMgmContext
    }

    private val groupReader: MembershipGroupReader = mock {
        on { lookup(eq(mgmX500Name)) } doReturn mgmMemberInfo
    }

    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val testProperties = mapOf(
        PropertyKeys.REGISTRATION_PROTOCOL to "reg-proto",
        PropertyKeys.SYNC_PROTOCOL to "sync-proto",
        PropertyKeys.SESSION_KEY_POLICY to "sess-pol",
        PropertyKeys.SESSION_PKI_MODE to "pki-sess",
        PropertyKeys.TLS_PKI_MODE to "pki-tls",
        PropertyKeys.TLS_VERSION to "tls-ver",
        PropertyKeys.SESSION_TRUST_ROOTS+".0" to "truststore-sess",
        PropertyKeys.TLS_TRUST_ROOTS+".0" to "truststore-tls",
        PropertyKeys.P2P_PROTOCOL_MODE to "p2p-mode",
        PropertyKeys.P2P_TLS_TYPE to "ONE_WAY",
    )

    class LayeredContextImpl(private val map: LayeredPropertyMap) : LayeredPropertyMap by map

    private val testPersistedGroupPolicyEntries: LayeredPropertyMap =
        LayeredPropertyMapMocks.create<LayeredContextImpl>(testProperties)
    private val membershipQueryResult = MembershipQueryResult.Success(testPersistedGroupPolicyEntries)
    private val membershipQueryClient: MembershipQueryClient = mock {
        on { queryGroupPolicy(eq(mgmHoldingIdentity)) } doReturn membershipQueryResult
    }

    private var processor = MemberOpsServiceProcessor(
        registrationProxy,
        virtualNodeInfoReadService,
        membershipGroupReaderProvider,
        membershipQueryClient,
        mock(),
        mock(),
        mock(),
        { mock() },
        clock,
        mock()
    )

    private fun assertResponseContext(expected: MembershipRpcRequestContext, actual: MembershipRpcResponseContext) {
        assertEquals(expected.requestId, actual.requestId)
        assertEquals(expected.requestTimestamp, actual.requestTimestamp)
        assertThat(actual.responseTimestamp.epochSecond).isGreaterThanOrEqualTo(expected.requestTimestamp.epochSecond)
        assertThat(actual.responseTimestamp.epochSecond).isLessThanOrEqualTo(now.epochSecond)
    }

    @Test
    fun `should successfully submit registration request`() {
        val requestTimestamp = now
        val requestId = UUID(0, 1).toString()
        val requestContext = MembershipRpcRequestContext(
            requestId,
            requestTimestamp
        )
        val request = MembershipRpcRequest(
            requestContext,
            RegistrationRpcRequest(
                mgmHoldingIdentity.shortHash.value,
                RegistrationRpcAction.REQUEST_JOIN,
                KeyValuePairList(emptyList())
            )
        )
        val future = CompletableFuture<MembershipRpcResponse>()
        processor.onNext(request, future)
        val result = future.get()
        val expectedResponse = RegistrationRpcResponse(
            requestId,
            requestTimestamp,
            RegistrationRpcStatus.SUBMITTED,
            null,
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
                clock.instant()
            ),
            mock()
        )
        val future = CompletableFuture<MembershipRpcResponse>()
        processor.onNext(request, future)
        val exception = assertThrows<ExecutionException> {
            future.get()
        }
        assertNotNull(exception.cause)
        assertThat(exception.cause).isInstanceOf(MembershipRegistrationException::class.java)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `should successfully submit generate group policy request`() {
        val requestTimestamp = now
        val requestContext = MembershipRpcRequestContext(
            UUID.randomUUID().toString(),
            requestTimestamp
        )
        val request = MembershipRpcRequest(
            requestContext,
            MGMGroupPolicyRequest(
                mgmHoldingIdentity.shortHash.value
            )
        )
        val future = CompletableFuture<MembershipRpcResponse>()
        processor.onNext(request, future)
        val result = future.get()
        assertSoftly {
            it.assertThat(result.response).isInstanceOf(MGMGroupPolicyResponse::class.java)
            val response = result.response as MGMGroupPolicyResponse
            val groupPolicy = response.groupPolicy
            it.assertThat(groupPolicy).contains("\"${FILE_FORMAT_VERSION}\":1")
            it.assertThat(groupPolicy).contains("\"${GROUP_ID}\":\"$MGM_GROUP_ID\"")
            it.assertThat(groupPolicy).contains("\"$REGISTRATION_PROTOCOL\":\"${testProperties[PropertyKeys.REGISTRATION_PROTOCOL]}\"")
            it.assertThat(groupPolicy).contains("\"$SYNC_PROTOCOL\":\"${testProperties[PropertyKeys.SYNC_PROTOCOL]}\"")
            it.assertThat(groupPolicy).contains("\"$PROTOCOL_PARAMETERS\"")
            it.assertThat(groupPolicy).contains("\"$SESSION_KEY_POLICY\":\"${testProperties[PropertyKeys.SESSION_KEY_POLICY]}\"")
            it.assertThat(groupPolicy).contains("\"$P2P_PARAMETERS\"")
            it.assertThat(groupPolicy).contains("\"$SESSION_TRUST_ROOTS\":[\"${testProperties[PropertyKeys.SESSION_TRUST_ROOTS+".0"]}\"]")
            it.assertThat(groupPolicy).contains("\"$TLS_TRUST_ROOTS\":[\"${testProperties[PropertyKeys.TLS_TRUST_ROOTS+".0"]}\"]")
            it.assertThat(groupPolicy).contains("\"$SESSION_PKI\":\"${testProperties[PropertyKeys.SESSION_PKI_MODE]}\"")
            it.assertThat(groupPolicy).contains("\"$TLS_PKI\":\"${testProperties[PropertyKeys.TLS_PKI_MODE]}\"")
            it.assertThat(groupPolicy).contains("\"$TLS_VERSION\":\"${testProperties[PropertyKeys.TLS_VERSION]}\"")
            it.assertThat(groupPolicy).contains("\"$PROTOCOL_MODE\":\"${testProperties[PropertyKeys.P2P_PROTOCOL_MODE]}\"")
            it.assertThat(groupPolicy).contains("\"$MGM_INFO\"")
            it.assertThat(groupPolicy).contains("\"corda.groupId\":\"$MGM_GROUP_ID\"")
            it.assertThat(groupPolicy).contains("\"$CIPHER_SUITE\"")
        }
    }

    @Nested
    inner class RegistrationStatusSpecificRpcRequestTests {
        @Test
        fun `should handle request for specific status when value exists`() {
            val requestTimestamp = now
            val registrationId = "id1"
            val requestContext = MembershipRpcRequestContext(
                UUID(0, 1).toString(),
                requestTimestamp
            )
            val request = MembershipRpcRequest(
                requestContext,
                RegistrationStatusSpecificRpcRequest(
                    mgmHoldingIdentity.shortHash.value,
                    registrationId
                )
            )
            val status = RegistrationRequestStatus(
                status = RegistrationStatus.APPROVED,
                registrationId = registrationId,
                memberContext = KeyValuePairList(emptyList()),
                protocolVersion = 3,
                registrationSent = clock.instant(),
                registrationLastModified = clock.instant()
            )
            val future = CompletableFuture<MembershipRpcResponse>()
            whenever(
                membershipQueryClient.queryRegistrationRequestStatus(
                    mgmHoldingIdentity,
                    registrationId
                )
            ).doReturn(MembershipQueryResult.Success(status))

            processor.onNext(request, future)

            val result = (future.get().response as? RegistrationStatusResponse)?.status
            assertSoftly { softly ->
                softly.assertThat(result?.registrationId).isEqualTo(registrationId)
                softly.assertThat(result?.registrationSent).isEqualTo(clock.instant())
                softly.assertThat(result?.registrationLastModified).isEqualTo(clock.instant())
                softly.assertThat(result?.registrationStatus).isEqualTo(RegistrationStatus.APPROVED)
                softly.assertThat(result?.registrationProtocolVersion).isEqualTo(3)
                softly.assertThat(result?.memberProvidedContext?.items).isEmpty()
            }
        }

        @Test
        fun `should handle request for specific status when value is missing`() {
            val requestTimestamp = now
            val registrationId = "id1"
            val requestContext = MembershipRpcRequestContext(
                UUID(0, 1).toString(),
                requestTimestamp
            )
            val request = MembershipRpcRequest(
                requestContext,
                RegistrationStatusSpecificRpcRequest(
                    mgmHoldingIdentity.shortHash.value,
                    registrationId
                )
            )
            val future = CompletableFuture<MembershipRpcResponse>()
            whenever(
                membershipQueryClient.queryRegistrationRequestStatus(
                    mgmHoldingIdentity,
                    registrationId
                )
            ).doReturn(MembershipQueryResult.Success(null))

            processor.onNext(request, future)

            val result = (future.get().response as? RegistrationStatusResponse)?.status
            assertThat(result).isNull()
        }

        @Test
        fun `should handle request for specific status when id is unknown`() {
            whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(mgmHoldingIdentity.shortHash)).doReturn(null)
            val requestTimestamp = now
            val registrationId = "id1"
            val requestContext = MembershipRpcRequestContext(
                UUID(0, 1).toString(),
                requestTimestamp
            )
            val request = MembershipRpcRequest(
                requestContext,
                RegistrationStatusSpecificRpcRequest(
                    mgmHoldingIdentity.shortHash.value,
                    registrationId
                )
            )
            val future = CompletableFuture<MembershipRpcResponse>()

            processor.onNext(request, future)

            val result = (future.get().response as? RegistrationStatusResponse)?.status
            assertThat(result).isNull()
        }
    }

    @Nested
    inner class RegistrationStatusRpcRequestTest {
        @Test
        fun `should handle request correctly`() {
            val requestTimestamp = now
            val requestContext = MembershipRpcRequestContext(
                UUID(0, 1).toString(),
                requestTimestamp
            )
            val request = MembershipRpcRequest(
                requestContext,
                RegistrationStatusRpcRequest(
                    mgmHoldingIdentity.shortHash.value,
                )
            )
            val statuses = (1..4).map {
                RegistrationRequestStatus(
                    status = RegistrationStatus.APPROVED,
                    registrationId = "id-$it",
                    memberContext = KeyValuePairList(emptyList()),
                    protocolVersion = 3,
                    registrationSent = clock.instant().plusSeconds(it.toLong()),
                    registrationLastModified = clock.instant().plusSeconds(2L*it.toLong())
                )
            }

            val future = CompletableFuture<MembershipRpcResponse>()
            whenever(
                membershipQueryClient.queryRegistrationRequestsStatus(
                    mgmHoldingIdentity,
                )
            ).doReturn(MembershipQueryResult.Success(statuses))

            processor.onNext(request, future)

            val result = (future.get().response as? RegistrationsStatusResponse)?.requests
            assertThat(result?.map { it.registrationId })
                .containsAll(statuses.map { it.registrationId })
        }

        @Test
        fun `should handle request for statuses when id is unknown`() {
            whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(mgmHoldingIdentity.shortHash)).doReturn(null)
            val requestTimestamp = now
            val requestContext = MembershipRpcRequestContext(
                UUID(0, 1).toString(),
                requestTimestamp
            )
            val request = MembershipRpcRequest(
                requestContext,
                RegistrationStatusRpcRequest(
                    mgmHoldingIdentity.shortHash.value,
                )
            )
            val future = CompletableFuture<MembershipRpcResponse>()

            processor.onNext(request, future)

            assertThat(future).isCompletedExceptionally
        }
    }
}
