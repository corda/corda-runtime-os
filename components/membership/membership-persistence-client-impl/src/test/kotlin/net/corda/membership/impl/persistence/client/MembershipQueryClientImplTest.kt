package net.corda.membership.impl.persistence.client

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.query.MutualTlsListAllowedCertificates
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.request.query.QueryPreAuthToken
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.ApprovalRulesQueryResponse
import net.corda.data.membership.db.response.query.ErrorKind
import net.corda.data.membership.db.response.query.GroupPolicyQueryResponse
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.data.membership.db.response.query.MutualTlsListAllowedCertificatesResponse
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.data.membership.db.response.query.PreAuthTokenQueryResponse
import net.corda.data.membership.db.response.query.RegistrationRequestQueryResponse
import net.corda.data.membership.db.response.query.RegistrationRequestsQueryResponse
import net.corda.data.membership.db.response.query.StaticNetworkInfoQueryResponse
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.Membership.MEMBERSHIP_DB_RPC_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

class MembershipQueryClientImplTest {

    lateinit var membershipQueryClient: MembershipQueryClient

    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val ourGroupId = "Group ID 1"
    private val ourHoldingIdentity = HoldingIdentity(ourX500Name, ourGroupId)
    private val ourMemberInfo: SelfSignedMemberInfo = mock()

    private val lifecycleEventCaptor = argumentCaptor<LifecycleEventHandler>()

    private val expectedConfigs = setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)

    private val clock = TestClock(Instant.ofEpochSecond(0))

    private val registrationHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()
    private val rpcSender: RPCSender<MembershipPersistenceRequest, MembershipPersistenceResponse> = mock()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn registrationHandle
    }
    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val publisherFactory: PublisherFactory = mock {
        on {
            createRPCSender(
                any<RPCConfig<MembershipPersistenceRequest, MembershipPersistenceResponse>>(),
                any()
            )
        } doReturn rpcSender
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private val memberInfoFactory: MemberInfoFactory = mock {
        on { createSelfSignedMemberInfo(any(), any(), any(), any()) } doReturn ourMemberInfo
    }
    private val layeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory(emptyList())

    private val testConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseString("instanceId=1"))

    private val serializedData = ByteBuffer.wrap(byteArrayOf(1))
    private val signedData = mock<SignedData> {
        on { data } doReturn serializedData
        on { signature } doReturn mock()
        on { signatureSpec } doReturn mock()
    }
    private val persistentMemberInfo: PersistentMemberInfo = mock {
        on { signedMemberContext } doReturn signedData
        on { serializedMgmContext } doReturn serializedData
    }

    private fun postStartEvent() {
        lifecycleEventCaptor.firstValue.processEvent(StartEvent(), coordinator)
    }

    private fun postStopEvent() {
        lifecycleEventCaptor.firstValue.processEvent(StopEvent(), coordinator)
    }

    private fun postRegistrationStatusChangeEvent(status: LifecycleStatus) {
        lifecycleEventCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(registrationHandle, status),
            coordinator
        )
    }

    private fun postConfigChangedEvent() {
        lifecycleEventCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG),
                mapOf(
                    ConfigKeys.BOOT_CONFIG to testConfig,
                    ConfigKeys.MESSAGING_CONFIG to testConfig
                )
            ),
            coordinator
        )
    }

    @BeforeEach
    fun setUp() {
        membershipQueryClient = MembershipQueryClientImpl(
            coordinatorFactory,
            publisherFactory,
            configurationReadService,
            memberInfoFactory,
            clock,
            layeredPropertyMapFactory
        )

        verify(coordinatorFactory).createCoordinator(any(), lifecycleEventCaptor.capture())
    }

    @Test
    fun `query all member infos before starting component`() {
        val result = membershipQueryClient.queryMemberInfo(ourHoldingIdentity)

        assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
    }

    @Test
    fun `query specific member info before starting component`() {
        val result = membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))

        assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
    }

    @Test
    fun `starting component starts coordinator`() {
        membershipQueryClient.start()
        verify(coordinator).start()
    }

    @Test
    fun `stopping component stops coordinator`() {
        membershipQueryClient.stop()
        verify(coordinator).stop()
    }

    @Test
    fun `registration handle created and closed as expected`() {
        postStartEvent()

        verify(registrationHandle, never()).close()
        verify(coordinator).followStatusChangesByName(any())

        postStartEvent()
        verify(registrationHandle).close()
        verify(coordinator, times(2)).followStatusChangesByName(any())

        postStopEvent()
        verify(registrationHandle, times(2)).close()
    }

    @Test
    fun `stop event sets status to down but closes no handlers since they haven't been created`() {
        postStopEvent()

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(registrationHandle, never()).close()
        verify(configHandle, never()).close()
        verify(rpcSender, never()).close()
    }

    @Test
    fun `component handles registration change as expected`() {
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)

        val configCaptor = argumentCaptor<Set<String>>()

        verify(configHandle, never()).close()
        verify(configurationReadService).registerComponentForUpdates(
            eq(coordinator),
            configCaptor.capture()
        )
        assertThat(configCaptor.firstValue).isEqualTo(expectedConfigs)

        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        verify(configHandle).close()
        verify(configurationReadService, times(2)).registerComponentForUpdates(
            eq(coordinator),
            any()
        )

        postRegistrationStatusChangeEvent(LifecycleStatus.DOWN)
        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())

        postRegistrationStatusChangeEvent(LifecycleStatus.ERROR)
        verify(coordinator, times(2)).updateStatus(eq(LifecycleStatus.DOWN), any())

        postStopEvent()
        verify(configHandle, times(2)).close()
    }

    @Test
    fun `config change event handled as expected`() {
        postConfigChangedEvent()

        val argCaptor = argumentCaptor<RPCConfig<MembershipPersistenceRequest, MembershipPersistenceResponse>>()
        verify(rpcSender, never()).close()
        verify(rpcSender).start()
        verify(publisherFactory).createRPCSender(argCaptor.capture(), any())
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())

        assertThat(argCaptor.firstValue.requestTopic).isEqualTo(MEMBERSHIP_DB_RPC_TOPIC)
        assertThat(argCaptor.firstValue.requestType).isEqualTo(MembershipPersistenceRequest::class.java)
        assertThat(argCaptor.firstValue.responseType).isEqualTo(MembershipPersistenceResponse::class.java)

        postConfigChangedEvent()
        verify(rpcSender).close()
        verify(rpcSender, times(2)).start()
        verify(publisherFactory, times(2)).createRPCSender(argCaptor.capture(), any())
        verify(coordinator, times(2)).updateStatus(eq(LifecycleStatus.UP), any())

        postStopEvent()
        verify(rpcSender, times(2)).close()
    }

    private fun buildResponse(
        rsContext: MembershipResponseContext,
        payload: Any?
    ) = MembershipPersistenceResponse(
        rsContext,
        payload
    )

    @Suppress("LongParameterList")
    private fun mockPersistenceResponse(
        payload: Any?,
        reqTimestampOverride: Instant? = null,
        reqIdOverride: String? = null,
        rsTimestampOverride: Instant? = null,
        holdingIdentityOverride: net.corda.data.identity.HoldingIdentity? = null,
    ) {
        whenever(rpcSender.sendRequest(any())).thenAnswer {
            val rsContext = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                MembershipResponseContext(
                    reqTimestampOverride ?: requestTimestamp,
                    reqIdOverride ?: requestId,
                    rsTimestampOverride ?: clock.instant(),
                    holdingIdentityOverride ?: holdingIdentity
                )
            }
            CompletableFuture.completedFuture(
                buildResponse(
                    rsContext,
                    payload
                )
            )
        }
    }

    @Test
    fun `request to persistence service is as expected`() {
        postConfigChangedEvent()
        mockPersistenceResponse(MemberInfoQueryResponse(listOf(persistentMemberInfo)))

        membershipQueryClient.queryMemberInfo(ourHoldingIdentity)

        with(argumentCaptor<MembershipPersistenceRequest>()) {
            verify(rpcSender).sendRequest(capture())

            assertThat(firstValue.context.requestTimestamp).isBeforeOrEqualTo(clock.instant())
            assertThat(firstValue.context.holdingIdentity)
                .isEqualTo(ourHoldingIdentity.toAvro())

            assertThat(firstValue.request).isInstanceOf(QueryMemberInfo::class.java)
            assertThat((firstValue.request as QueryMemberInfo).queryIdentities).isEmpty()
        }
    }

    @Test
    fun `successful request for all member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(MemberInfoQueryResponse(listOf(persistentMemberInfo)))

        val queryResult = membershipQueryClient.queryMemberInfo(ourHoldingIdentity)
        assertThat(queryResult)
            .isInstanceOf(MembershipQueryResult.Success::class.java)
        assertThat((queryResult as MembershipQueryResult.Success).payload).hasSize(1)
    }

    @Test
    fun `failed request for all member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(PersistenceFailedResponse("Error", ErrorKind.GENERAL))

        assertThat(membershipQueryClient.queryMemberInfo(ourHoldingIdentity)).isInstanceOf(
            MembershipQueryResult.Failure::class.java
        )
    }

    @Test
    fun `successful request for list of member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(MemberInfoQueryResponse(listOf(persistentMemberInfo)))

        val queryResult = membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))
        assertThat(queryResult)
            .isInstanceOf(MembershipQueryResult.Success::class.java)
        assertThat((queryResult as MembershipQueryResult.Success).payload).hasSize(1)
    }

    @Test
    fun `failed request for list of member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(PersistenceFailedResponse("Error", ErrorKind.GENERAL))

        assertThat(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))).isInstanceOf(
            MembershipQueryResult.Failure::class.java
        )
    }

    @Test
    fun `successful request for member info with no results is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(MemberInfoQueryResponse(emptyList()))

        assertThat(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))).isInstanceOf(
            MembershipQueryResult.Success::class.java
        )
    }

    @Test
    fun `Mismatch in holding identity between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            MemberInfoQueryResponse(emptyList()),
            holdingIdentityOverride = net.corda.data.identity.HoldingIdentity("O=BadName,L=London,C=GB", "BAD_ID")
        )
        assertThat(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))).isInstanceOf(
            MembershipQueryResult.Failure::class.java
        )
    }

    @Test
    fun `Mismatch in request timestamp between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            MemberInfoQueryResponse(emptyList()),
            reqTimestampOverride = clock.instant().plusSeconds(5)
        )
        assertThat(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))).isInstanceOf(
            MembershipQueryResult.Failure::class.java
        )
    }

    @Test
    fun `Mismatch in request ID between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            MemberInfoQueryResponse(emptyList()),
            reqIdOverride = "Group ID 3"
        )
        assertThat(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))).isInstanceOf(
            MembershipQueryResult.Failure::class.java
        )
    }

    @Test
    fun `Response timestamp before request timestamp causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            MemberInfoQueryResponse(emptyList()),
            rsTimestampOverride = clock.instant().minusSeconds(10)
        )
        assertThat(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))).isInstanceOf(
            MembershipQueryResult.Failure::class.java
        )
    }

    @Test
    fun `successful request for group policy`() {
        postConfigChangedEvent()

        whenever(rpcSender.sendRequest(any())).thenAnswer {
            val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                MembershipResponseContext(
                    requestTimestamp,
                    requestId,
                    clock.instant(),
                    holdingIdentity
                )
            }
            CompletableFuture.completedFuture(
                MembershipPersistenceResponse(
                    context,
                    GroupPolicyQueryResponse(KeyValuePairList(listOf(KeyValuePair("Key", "Value"))), 101L)
                )
            )
        }

        val result = membershipQueryClient.queryGroupPolicy(ourHoldingIdentity)
        assertThat(result.getOrThrow()).isNotNull
        assertThat(result.getOrThrow().first.entries.size).isEqualTo(1)
        assertThat(result.getOrThrow().second).isEqualTo(101L)
    }

    @Test
    fun `when no group policy found empty property map is created`() {
        postConfigChangedEvent()

        whenever(rpcSender.sendRequest(any())).thenAnswer {
            val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                MembershipResponseContext(
                    requestTimestamp,
                    requestId,
                    clock.instant(),
                    holdingIdentity
                )
            }
            CompletableFuture.completedFuture(
                MembershipPersistenceResponse(
                    context,
                    GroupPolicyQueryResponse(KeyValuePairList(emptyList()), 0L)
                )
            )
        }

        val result = membershipQueryClient.queryGroupPolicy(ourHoldingIdentity)
        assertThat(result.getOrThrow()).isNotNull
        assertThat(result.getOrThrow().first.entries).isEmpty()
        assertThat(result.getOrThrow().second).isEqualTo(0L)
    }

    @Nested
    inner class QueryRegistrationRequestStatusTests {
        @Test
        fun `it will returns the correct data in case of successful valid result`() {
            postConfigChangedEvent()
            val status =
                RegistrationRequestDetails(
                    clock.instant(),
                    clock.instant(),
                    RegistrationStatus.PENDING_AUTO_APPROVAL,
                    "id",
                    "holdingId1",
                    1,
                    SignedData(
                        ByteBuffer.wrap(byteArrayOf(0)),
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap("pk1".toByteArray()),
                            ByteBuffer.wrap("ct1".toByteArray())
                        ),
                        CryptoSignatureSpec("dummy", null, null),
                    ),
                    SignedData(
                        ByteBuffer.wrap(byteArrayOf(1)),
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap("pk2".toByteArray()),
                            ByteBuffer.wrap("ct2".toByteArray())
                        ),
                        CryptoSignatureSpec("dummy2", null, null),
                    ),
                    "test reason",
                    0L,
                )
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        RegistrationRequestQueryResponse(status)
                    )
                )
            }

            val result = membershipQueryClient.queryRegistrationRequest(ourHoldingIdentity, "id")

            assertThat(result.getOrThrow()).isEqualTo(status)
        }
        @Test
        fun `it will returns the correct data in case of successful null result`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        RegistrationRequestQueryResponse(null)
                    )
                )
            }

            val result = membershipQueryClient.queryRegistrationRequest(ourHoldingIdentity, "id")

            assertThat(result.getOrThrow())
                .isNull()
        }
        @Test
        fun `it will returns an error when the result is unexpected`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        GroupPolicyQueryResponse()
                    )
                )
            }

            val result = membershipQueryClient.queryRegistrationRequest(ourHoldingIdentity, "id")

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }
        @Test
        fun `it will returns an error when the result is an error`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        PersistenceFailedResponse("oops", ErrorKind.GENERAL)
                    )
                )
            }

            val result = membershipQueryClient.queryRegistrationRequest(ourHoldingIdentity, "id")

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }
        @Test
        fun `it will returns an error when the result not right`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val myContext = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                val response = mock<MembershipPersistenceResponse> {
                    on { context } doReturn myContext
                    on { payload } doReturn null
                }
                CompletableFuture.completedFuture(response)
            }

            val result = membershipQueryClient.queryRegistrationRequest(ourHoldingIdentity, "id")

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }

    }

    @Nested
    inner class QueryRegistrationRequestsStatusTests {
        @Test
        fun `it will returns the correct data in case of successful valid result`() {
            postConfigChangedEvent()
            val statuses = listOf(
                RegistrationRequestDetails(
                    clock.instant(),
                    clock.instant(),
                    RegistrationStatus.PENDING_MANUAL_APPROVAL,
                    "id 1",
                    "holdingId1",
                    1,
                    SignedData(
                        ByteBuffer.wrap(byteArrayOf(0)),
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap("pk1".toByteArray()),
                            ByteBuffer.wrap("ct1".toByteArray())
                        ),
                        CryptoSignatureSpec("dummy1", null, null),
                    ),
                    SignedData(
                        ByteBuffer.wrap(byteArrayOf(1)),
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap("pk3".toByteArray()),
                            ByteBuffer.wrap("ct3".toByteArray())
                        ),
                        CryptoSignatureSpec("dummy3", null, null),
                    ),
                    "test reason 1",
                    0L,
                ),
                RegistrationRequestDetails(
                    clock.instant(),
                    clock.instant(),
                    RegistrationStatus.PENDING_AUTO_APPROVAL,
                    "id 2",
                    "holdingId2",
                    1,
                    SignedData(
                        ByteBuffer.wrap(byteArrayOf(0)),
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap("pk2".toByteArray()),
                            ByteBuffer.wrap("ct2".toByteArray())
                        ),
                        CryptoSignatureSpec("dummy2", null, null),
                    ),
                    SignedData(
                        ByteBuffer.wrap(byteArrayOf(1)),
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap("pk4".toByteArray()),
                            ByteBuffer.wrap("ct4".toByteArray())
                        ),
                        CryptoSignatureSpec("dummy4", null, null),
                    ),
                    "test reason 2",
                    1L,
                ),
            )
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        RegistrationRequestsQueryResponse(statuses)
                    )
                )
            }

            val result = membershipQueryClient.queryRegistrationRequests(ourHoldingIdentity)

            assertThat(result.getOrThrow()).isEqualTo(statuses)
        }
        @Test
        fun `it will returns an error when the result is unexpected`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        GroupPolicyQueryResponse()
                    )
                )
            }

            val result = membershipQueryClient.queryRegistrationRequests(ourHoldingIdentity)

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }
        @Test
        fun `it will returns an error when the result is an error`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        PersistenceFailedResponse("oops", ErrorKind.GENERAL)
                    )
                )
            }

            val result = membershipQueryClient.queryRegistrationRequests(ourHoldingIdentity)

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }
        @Test
        fun `it will returns an error when the result not right`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val myContext = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                val response = mock<MembershipPersistenceResponse> {
                    on { context } doReturn myContext
                    on { payload } doReturn null
                }
                CompletableFuture.completedFuture(response)
            }

            val result = membershipQueryClient.queryRegistrationRequests(ourHoldingIdentity)

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }
    }

    @Nested
    inner class MutualTlsQueryTest {
        @Test
        fun `mutualTlsListAllowedCertificates sends the correct request`() {
            postConfigChangedEvent()
            val argument = argumentCaptor<MembershipPersistenceRequest>()
            val response = CompletableFuture.completedFuture(mock<MembershipPersistenceResponse>())
            whenever(rpcSender.sendRequest(argument.capture())).thenReturn(response)

            membershipQueryClient.mutualTlsListAllowedCertificates(
                ourHoldingIdentity,
            )

            assertThat(argument.firstValue.request).isInstanceOf(MutualTlsListAllowedCertificates::class.java)
        }

        @Test
        fun `mutualTlsListAllowedCertificates return the correct result if the request was successful`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val request = it.getArgument<MembershipPersistenceRequest>(0)
                val context = MembershipResponseContext(
                    request.context.requestTimestamp,
                    request.context.requestId,
                    clock.instant(),
                    request.context.holdingIdentity,
                )

                CompletableFuture.completedFuture(
                    buildResponse(
                        context,
                        MutualTlsListAllowedCertificatesResponse(
                            listOf(
                                ourX500Name.toString(),
                            )
                        )
                    )
                )
            }

            val response = membershipQueryClient.mutualTlsListAllowedCertificates(
                ourHoldingIdentity,
            )

            assertThat((response as? MembershipQueryResult.Success)?.payload).containsExactly(ourX500Name.toString())
        }

        @Test
        fun `mutualTlsListAllowedCertificates return error if the query failed`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val request = it.getArgument<MembershipPersistenceRequest>(0)
                val context = MembershipResponseContext(
                    request.context.requestTimestamp,
                    request.context.requestId,
                    clock.instant(),
                    request.context.holdingIdentity,
                )

                CompletableFuture.completedFuture(
                    buildResponse(
                        context,
                        PersistenceFailedResponse("Error", ErrorKind.GENERAL)
                    )
                )
            }

            val response = membershipQueryClient.mutualTlsListAllowedCertificates(
                ourHoldingIdentity,
            )

            assertThat(response).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }

        @Test
        fun `mutualTlsListAllowedCertificates return error if the result is unknown`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val request = it.getArgument<MembershipPersistenceRequest>(0)
                val context = MembershipResponseContext(
                    request.context.requestTimestamp,
                    request.context.requestId,
                    clock.instant(),
                    request.context.holdingIdentity,
                )

                CompletableFuture.completedFuture(
                    buildResponse(
                        context,
                        "ooops"
                    )
                )
            }

            val response = membershipQueryClient.mutualTlsListAllowedCertificates(
                ourHoldingIdentity,
            )

            assertThat(response).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }
    }

    @Nested
    inner class QueryApprovalRulesTests {
        @Test
        fun `getApprovalRules returns the correct list of rules`() {
            val rules = listOf(ApprovalRuleDetails("rule-id", "rule-regex", "rule-label"))
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        ApprovalRulesQueryResponse(rules)
                    )
                )
            }

            val result = membershipQueryClient.getApprovalRules(
                ourHoldingIdentity,
                ApprovalRuleType.STANDARD,
            )

            assertThat(result.getOrThrow()).isEqualTo(rules)
        }

        @Test
        fun `getApprovalRules returns error in case of failure`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        PersistenceFailedResponse("oops", ErrorKind.GENERAL)
                    )
                )
            }

            val result = membershipQueryClient.getApprovalRules(ourHoldingIdentity, ApprovalRuleType.STANDARD)

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }

        @Test
        fun `getApprovalRules returns failure for unexpected result`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        GroupPolicyQueryResponse()
                    )
                )
            }

            val result = membershipQueryClient.getApprovalRules(ourHoldingIdentity, ApprovalRuleType.STANDARD)

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }

        @Test
        fun `getApprovalRules returns an error when the result not right`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val myContext = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp, requestId, clock.instant(), holdingIdentity
                    )
                }
                val response = mock<MembershipPersistenceResponse> {
                    on { context } doReturn myContext
                    on { payload } doReturn null
                }
                CompletableFuture.completedFuture(response)
            }

            val result = membershipQueryClient.getApprovalRules(ourHoldingIdentity, ApprovalRuleType.STANDARD)

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }
    }

    @Nested
    inner class QueryPreAuthTokenTests {

        @Test
        fun `queryPreAuthToken sends the correct request if viewInactive`() {
            postConfigChangedEvent()
            val tokenId = UUID.randomUUID()
            val capture = argumentCaptor<MembershipPersistenceRequest>()
            whenever(rpcSender.sendRequest(capture.capture())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        PreAuthTokenQueryResponse(mock())
                    )
                )
            }

            membershipQueryClient.queryPreAuthTokens(
                ourHoldingIdentity,
                ourX500Name,
                tokenId,
                true
            )
            assertThat(capture.firstValue.request).isInstanceOf(QueryPreAuthToken::class.java)
            val request = capture.firstValue.request as QueryPreAuthToken
            assertThat(request.tokenId).isEqualTo(tokenId.toString())
            assertThat(request.ownerX500Name).isEqualTo(ourX500Name.toString())
            assertThat(request.statuses).containsExactlyInAnyOrderElementsOf(PreAuthTokenStatus.values().toList())
        }

        @Test
        fun `queryPreAuthToken sends the correct request if not viewInactive`() {
            postConfigChangedEvent()
            val tokenId = UUID.randomUUID()
            val capture = argumentCaptor<MembershipPersistenceRequest>()
            whenever(rpcSender.sendRequest(capture.capture())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        PreAuthTokenQueryResponse(mock())
                    )
                )
            }

            membershipQueryClient.queryPreAuthTokens(
                ourHoldingIdentity,
                ourX500Name,
                tokenId,
                false
            )
            assertThat(capture.firstValue.request).isInstanceOf(QueryPreAuthToken::class.java)
            val request = capture.firstValue.request as QueryPreAuthToken
            assertThat(request.tokenId).isEqualTo(tokenId.toString())
            assertThat(request.ownerX500Name).isEqualTo(ourX500Name.toString())
            assertThat(request.statuses).containsOnly(PreAuthTokenStatus.AVAILABLE)
        }

        @Test
        fun `queryPreAuthToken returns the correct list of tokens`() {
            val tokens = listOf(PreAuthToken())
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        PreAuthTokenQueryResponse(tokens)
                    )
                )
            }

            val result = membershipQueryClient.queryPreAuthTokens(ourHoldingIdentity, null, null, true)

            assertThat(result.getOrThrow()).isEqualTo(tokens)
        }

        @Test
        fun `queryPreAuthTokens returns error in case of failure`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        PersistenceFailedResponse("oops", ErrorKind.GENERAL)
                    )
                )
            }

            val result = membershipQueryClient.queryPreAuthTokens(ourHoldingIdentity, null, null, true)

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }

        @Test
        fun `queryPreAuthTokens returns failure for unexpected result`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val context = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp,
                        requestId,
                        clock.instant(),
                        holdingIdentity
                    )
                }
                CompletableFuture.completedFuture(
                    MembershipPersistenceResponse(
                        context,
                        GroupPolicyQueryResponse()
                    )
                )
            }

            val result = membershipQueryClient.queryPreAuthTokens(ourHoldingIdentity, null, null, true)

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }

        @Test
        fun `getApprovalRules returns an error when the result not right`() {
            postConfigChangedEvent()
            whenever(rpcSender.sendRequest(any())).thenAnswer {
                val myContext = with((it.arguments.first() as MembershipPersistenceRequest).context) {
                    MembershipResponseContext(
                        requestTimestamp, requestId, clock.instant(), holdingIdentity
                    )
                }
                val response = mock<MembershipPersistenceResponse> {
                    on { context } doReturn myContext
                    on { payload } doReturn null
                }
                CompletableFuture.completedFuture(response)
            }

            val result = membershipQueryClient.queryPreAuthTokens(ourHoldingIdentity, null, null, true)

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }
    }

    @Nested
    inner class QueryStaticNetworkInfo {
        private val groupId = UUID(0, 1).toString()
        private val groupParameters = KeyValuePairList(emptyList())
        private val mgmPublicSigningKey = ByteBuffer.wrap("123".toByteArray())
        private val mgmPrivateSigningKey = ByteBuffer.wrap("456".toByteArray())
        private val version = 1

        private val info = StaticNetworkInfo(
            groupId,
            groupParameters,
            mgmPublicSigningKey,
            mgmPrivateSigningKey,
            version
        )

        @Test
        fun `Can successfully query`() {
            postConfigChangedEvent()
            mockPersistenceResponse(StaticNetworkInfoQueryResponse(info))

            val result = membershipQueryClient.queryStaticNetworkInfo(groupId)

            assertThat(result).isInstanceOf(MembershipQueryResult.Success::class.java)
            assertThat(result.getOrThrow()).isEqualTo(info)
        }

        @Test
        fun `Failure to get expected response results in failure`() {
            class BadResponse
            postConfigChangedEvent()
            mockPersistenceResponse(BadResponse())

            val result = membershipQueryClient.queryStaticNetworkInfo(groupId)

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
            assertThat((result as MembershipQueryResult.Failure).errorMsg).contains("Failed to retrieve static network")
        }
    }
}
