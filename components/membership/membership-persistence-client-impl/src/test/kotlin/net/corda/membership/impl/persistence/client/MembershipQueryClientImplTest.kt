package net.corda.membership.impl.persistence.client

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.GroupPolicyQueryResponse
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.data.membership.db.response.query.MemberSignature
import net.corda.data.membership.db.response.query.MemberSignatureQueryResponse
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.impl.LayeredPropertyMapFactoryImpl
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_DB_RPC_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import java.util.*
import java.util.concurrent.CompletableFuture

class MembershipQueryClientImplTest {

    lateinit var membershipQueryClient: MembershipQueryClient

    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val ourGroupId = "Group ID 1"
    private val ourHoldingIdentity = HoldingIdentity(ourX500Name.toString(), ourGroupId)
    private val ourMemberInfo: MemberInfo = mock()
    private val registrationId = "Group ID 2"

    private val lifecycleEventCaptor = argumentCaptor<LifecycleEventHandler>()

    private val expectedConfigs = setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)

    private val clock = TestClock(Instant.ofEpochSecond(0))

    private val registrationHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()
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
        on { create(any()) } doReturn ourMemberInfo
    }
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = LayeredPropertyMapFactoryImpl(emptyList())

    private val testConfig =
        SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString("instanceId=1"))

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
            coordinatorFactory, publisherFactory, configurationReadService, memberInfoFactory, clock, layeredPropertyMapFactory
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
    fun `query registration request before starting component`() {
        // Function is not yet implemented
        assertThrows<UnsupportedOperationException> {
            membershipQueryClient.queryRegistrationRequest(ourHoldingIdentity, registrationId)
        }
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

    fun buildResponse(
        rsContext: MembershipResponseContext,
        success: Boolean,
        payload: Any?
    ) = MembershipPersistenceResponse(
        rsContext,
        if (success) payload else PersistenceFailedResponse("Error")
    )

    @Suppress("LongParameterList")
    fun mockPersistenceResponse(
        success: Boolean,
        payload: List<PersistentMemberInfo>?,
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
                    success,
                    payload?.let { MemberInfoQueryResponse(it) }
                )
            )
        }
    }

    @Test
    fun `request to persistence service is as expected`() {
        postConfigChangedEvent()
        mockPersistenceResponse(true, listOf(mock()))

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
        mockPersistenceResponse(true, listOf(mock()))

        val queryResult = membershipQueryClient.queryMemberInfo(ourHoldingIdentity)
        assertThat(queryResult)
            .isInstanceOf(MembershipQueryResult.Success::class.java)
        assertThat((queryResult as MembershipQueryResult.Success).payload).hasSize(1)
    }

    @Test
    fun `failed request for all member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(false, null)

        assertThat(membershipQueryClient.queryMemberInfo(ourHoldingIdentity)).isInstanceOf(
            MembershipQueryResult.Failure::class.java
        )
    }

    @Test
    fun `successful request for list of member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(true, listOf(mock()))

        val queryResult = membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))
        assertThat(queryResult)
            .isInstanceOf(MembershipQueryResult.Success::class.java)
        assertThat((queryResult as MembershipQueryResult.Success).payload).hasSize(1)
    }

    @Test
    fun `failed request for list of member info is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(false, null)

        assertThat(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))).isInstanceOf(
            MembershipQueryResult.Failure::class.java
        )
    }

    @Test
    fun `successful request for member info with no results is correct`() {
        postConfigChangedEvent()
        mockPersistenceResponse(true, emptyList())

        assertThat(membershipQueryClient.queryMemberInfo(ourHoldingIdentity, listOf(ourHoldingIdentity))).isInstanceOf(
            MembershipQueryResult.Success::class.java
        )
    }

    @Test
    fun `Mismatch in holding identity between RQ and RS causes failed response`() {
        postConfigChangedEvent()
        mockPersistenceResponse(
            true,
            emptyList(),
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
            true,
            emptyList(),
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
            true,
            emptyList(),
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
            true,
            emptyList(),
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
                    GroupPolicyQueryResponse(KeyValuePairList(listOf(KeyValuePair("Key", "Value"))))
                )
            )
        }

        val result = membershipQueryClient.queryGroupPolicy(ourHoldingIdentity)
        assertThat(result.getOrThrow()).isNotNull
        assertThat(result.getOrThrow().entries.size).isEqualTo(1)
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
                    GroupPolicyQueryResponse(KeyValuePairList(emptyList()))
                )
            )
        }

        val result = membershipQueryClient.queryGroupPolicy(ourHoldingIdentity)
        assertThat(result.getOrThrow()).isNotNull
        assertThat(result.getOrThrow().entries).isEmpty()
    }

    @Nested
    inner class QueryMembersSignaturesTests {
        @Test
        fun `it will returns an empty response for empty query`() {
            val result = membershipQueryClient.queryMembersSignatures(ourHoldingIdentity, emptyList())

            assertThat(result.getOrThrow()).isEmpty()
        }

        @Test
        fun `it will returns the correct data in case of successful result`() {
            val bob = HoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
            postConfigChangedEvent()
            val holdingId1 = HoldingIdentity("O=Alice ,L=London, C=GB", ourGroupId)
            val signature1 = CryptoSignatureWithKey(
                ByteBuffer.wrap("pk1".toByteArray()),
                ByteBuffer.wrap("ct1".toByteArray()),
                KeyValuePairList(emptyList()),
            )
            val holdingId2 = HoldingIdentity("O=Donald ,L=London, C=GB", ourGroupId)
            val signature2 = CryptoSignatureWithKey(
                ByteBuffer.wrap("pk2".toByteArray()),
                ByteBuffer.wrap("ct2".toByteArray()),
                KeyValuePairList(emptyList()),
            )
            val signatures = listOf(
                MemberSignature(
                    holdingId1.toAvro(), signature1
                ),
                MemberSignature(
                    holdingId2.toAvro(), signature2
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
                        MemberSignatureQueryResponse(signatures)
                    )
                )
            }

            val result = membershipQueryClient.queryMembersSignatures(ourHoldingIdentity, listOf(bob))

            assertThat(result.getOrThrow())
                .containsEntry(
                    holdingId1, signature1
                )
                .containsEntry(
                    holdingId2, signature2
                )
        }

        @Test
        fun `it will return error for failure`() {
            val bob = HoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
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
                        PersistenceFailedResponse("oops")
                    )
                )
            }

            val result = membershipQueryClient.queryMembersSignatures(ourHoldingIdentity, listOf(bob))

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }
        @Test
        fun `it will return error for invalid reply`() {
            val bob = HoldingIdentity("O=Bob ,L=London, C=GB", ourGroupId)
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
                        "Nop"
                    )
                )
            }

            val result = membershipQueryClient.queryMembersSignatures(ourHoldingIdentity, listOf(bob))

            assertThat(result).isInstanceOf(MembershipQueryResult.Failure::class.java)
        }
    }
}
