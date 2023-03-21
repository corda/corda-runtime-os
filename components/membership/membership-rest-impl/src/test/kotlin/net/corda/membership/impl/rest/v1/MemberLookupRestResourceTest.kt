package net.corda.membership.impl.rest.v1

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.rest.v1.types.response.RestMemberInfo
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

class MemberLookupRestResourceTest {
    private companion object {
        const val KNOWN_KEY = "12345"
        const val HOLDING_IDENTITY_STRING = "1234567890ab"
        const val MGM_HOLDING_IDENTITY_STRING = "1234567890cd"
        val BAD_HOLDING_IDENTITY = ShortHash.of("deaddeaddead")
        val clock = TestClock(Instant.ofEpochSecond(100))
    }

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
    }

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }

    private val knownKey: PublicKey = mock()
    private val keys = listOf(knownKey, knownKey)

    private val endpointInfoFactory: EndpointInfoFactory = mock {
        on { create(any(), any()) } doAnswer { invocation ->
            mock {
                on { this.url } doReturn invocation.getArgument(0)
                on { this.protocolVersion } doReturn invocation.getArgument(1)
            }
        }
    }
    private val endpoints = listOf(
        endpointInfoFactory.create("https://corda5.r3.com:10000"),
        endpointInfoFactory.create("https://corda5.r3.com:10001", 10)
    )

    private val holdingIdentity = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "0")
    private val mgmName = "O=MGM, L=London, C=GB"
    private val mgmHoldingIdentity  = createTestHoldingIdentity(mgmName, "0")

    private val keyEncodingService: CipherSchemeMetadata = mock {
        on { decodePublicKey(KNOWN_KEY) } doReturn knownKey
        on { encodeAsString(knownKey) } doReturn KNOWN_KEY
    }

    private val converters = listOf(
        EndpointInfoConverter(),
        MemberNotaryDetailsConverter(keyEncodingService),
        PublicKeyConverter(keyEncodingService)
    )

    private val memberInfoFactory = MemberInfoFactoryImpl(LayeredPropertyMapMocks.createFactory(converters))

    private val alice = createMemberInfo("CN=Alice,O=Alice,OU=Unit1,L=London,ST=State1,C=GB")
    private val bob = createMemberInfo("CN=Bob,O=Bob,OU=Unit2,L=Dublin,ST=State2,C=IE")
    private val charlie = createMemberInfo("CN=Charlie,O=Charlie,OU=Unit2,L=Dublin,ST=State2,C=IE", MEMBER_STATUS_SUSPENDED)
    private val mgm = createMemberInfo(mgmName, isMgm = true)
    private val memberInfoList = listOf(alice, bob, charlie)

    private val aliceResult = alice.toRestMemberInfo()
    private val bobResult = bob.toRestMemberInfo()
    private val mgmResult = mgm.toRestMemberInfo()
    private val charlieResult = charlie.toRestMemberInfo()

    @Suppress("SpreadOperator")
    private fun createMemberInfo(
        name: String,
        memberStatus: String = MEMBER_STATUS_ACTIVE,
        isMgm: Boolean = false,
    ): MemberInfo = memberInfoFactory.create(
        sortedMapOf(
            PARTY_NAME to name,
            PARTY_SESSION_KEY to KNOWN_KEY,
            GROUP_ID to "DEFAULT_MEMBER_GROUP_ID",
            *convertPublicKeys().toTypedArray(),
            *convertEndpoints().toTypedArray(),
            SOFTWARE_VERSION to "5.0.0",
            PLATFORM_VERSION to "5000",
        ),
        sortedMapOf(
            STATUS to memberStatus,
            MODIFIED_TIME to clock.instant().toString(),
            SERIAL to "1",
            IS_MGM to isMgm.toString()
        )
    )

    private fun MemberInfo.toRestMemberInfo() = RestMemberInfo(
        memberProvidedContext.entries.associate { it.key to it.value },
        mgmProvidedContext.entries.associate { it.key to it.value },
    )

    private fun convertPublicKeys(): List<Pair<String, String>> =
        keys.mapIndexed { index, ledgerKey ->
            String.format(
                LEDGER_KEYS_KEY,
                index
            ) to keyEncodingService.encodeAsString(ledgerKey)
        }

    private fun convertEndpoints(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (index in endpoints.indices) {
            result.add(
                Pair(
                    String.format(URL_KEY, index),
                    endpoints[index].url
                )
            )
            result.add(
                Pair(
                    String.format(PROTOCOL_VERSION, index),
                    endpoints[index].protocolVersion.toString()
                )
            )
        }
        return result
    }

    private val testEntries = mapOf(
        MPV_KEY to "1",
        EPOCH_KEY to "1",
        MODIFIED_TIME_KEY to clock.instant().toString(),
    )
    private val mockGroupParameters = mock<GroupParameters> {
        on { entries } doReturn testEntries.entries
    }
    private val groupReader: MembershipGroupReader = mock {
        on { lookup(MembershipStatusFilter.ACTIVE_OR_SUSPENDED) } doReturn memberInfoList
        on { lookup(holdingIdentity.x500Name) } doReturn alice
        on { lookup(mgmHoldingIdentity.x500Name) } doReturn mgm
        on { groupParameters } doReturn mockGroupParameters
    }

    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(ShortHash.of(HOLDING_IDENTITY_STRING)) } doReturn VirtualNodeInfo(
            holdingIdentity,
            CpiIdentifier("test", "test", SecureHashImpl("algorithm", "1234".toByteArray())),
            null,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            timestamp = Instant.now()
        )
        on { getByHoldingIdentityShortHash(ShortHash.of(MGM_HOLDING_IDENTITY_STRING)) } doReturn VirtualNodeInfo(
            mgmHoldingIdentity,
            CpiIdentifier("test", "test", SecureHashImpl("algorithm", "1234".toByteArray())),
            null,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            timestamp = Instant.now()
        )
    }

    private val memberLookupRestResource = MemberLookupRestResourceImpl(
        lifecycleCoordinatorFactory,
        membershipGroupReaderProvider,
        virtualNodeInfoReadService
    )

    private fun startService() {
        memberLookupRestResource.start()
        memberLookupRestResource.activate("")
    }

    private fun stopService() {
        memberLookupRestResource.deactivate("")
        memberLookupRestResource.stop()
    }

    @Test
    fun `starting and stopping the service succeeds`() {
        memberLookupRestResource.start()
        assertThat(memberLookupRestResource.isRunning).isTrue
        memberLookupRestResource.stop()
        assertThat(memberLookupRestResource.isRunning).isFalse
    }

    @Test
    fun `exception should be thrown when service is not running`() {
        val ex =
            assertThrows<ServiceUnavailableException> { memberLookupRestResource.lookup(BAD_HOLDING_IDENTITY.value) }
        assertThat(ex).hasMessageContaining("MemberLookupRestResourceImpl")
    }

    @Nested
    inner class LookupTests {

        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `unfiltered lookup returns a list of all active members and their contexts`() {
            val result = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING).members

            assertThat(result).containsExactlyInAnyOrder(aliceResult, bobResult)

            whenever(groupReader.lookup(MembershipStatusFilter.ACTIVE_OR_SUSPENDED))
                .doReturn(listOf(mgm, alice, bob, charlie))

            val result2 = memberLookupRestResource.lookup(
                MGM_HOLDING_IDENTITY_STRING,
            ).members

            assertThat(result2).containsExactlyInAnyOrder(aliceResult, bobResult, mgmResult)
        }

        @Test
        fun `lookup filtered by common name (CN) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, commonName = "bob")

            assertThat(result1.members).containsExactlyInAnyOrder(bobResult)

            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, commonName = "BOB")

            assertThat(result2.members).containsExactlyInAnyOrder(bobResult)
        }

        @Test
        fun `lookup filtered by organization (O) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, organization = "ALICE")

            assertThat(result1.members).containsExactlyInAnyOrder(aliceResult)

            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, organization = "alice")

            assertThat(result2.members).containsExactlyInAnyOrder(aliceResult)
        }

        @Test
        fun `lookup filtered by organization unit (OU) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, organizationUnit = "unit2")

            assertThat(result1.members).containsExactlyInAnyOrder(bobResult)

            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, organizationUnit = "UNIT2")

            assertThat(result2.members).containsExactlyInAnyOrder(bobResult)
        }

        @Test
        fun `lookup filtered by locality (L) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, locality = "london")

            assertThat(result1.members).containsExactlyInAnyOrder(aliceResult)

            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, locality = "LONDON")

            assertThat(result2.members).containsExactlyInAnyOrder(aliceResult)
        }

        @Test
        fun `lookup filtered by state (ST) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, state = "state2")

            assertThat(result1.members).containsExactlyInAnyOrder(bobResult)

            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, state = "state2")

            assertThat(result2.members).containsExactlyInAnyOrder(bobResult)
        }

        @Test
        fun `lookup filtered by country (C) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, country = "gb")

            assertThat(result1.members).containsExactlyInAnyOrder(aliceResult)

            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, country = "GB")

            assertThat(result2.members).containsExactlyInAnyOrder(aliceResult)
        }

        @Test
        fun `lookup filtered by all attributes is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(
                HOLDING_IDENTITY_STRING,
                "bob",
                "bob",
                "unit2",
                "dublin",
                "state2",
                "ie"
            )

            assertThat(result1.members).containsExactlyInAnyOrder(bobResult)

            val result2 = memberLookupRestResource.lookup(
                HOLDING_IDENTITY_STRING,
                "BOB",
                "BOB",
                "UNIT2",
                "DUBLIN",
                "STATE2",
                "IE"
            )

            assertThat(result2.members).containsExactlyInAnyOrder(bobResult)
        }

        @Test
        fun `lookup by member filtered by statuses returns active members but not suspended members`() {
            val result1 = memberLookupRestResource.lookup(
                HOLDING_IDENTITY_STRING,
                statuses = listOf(MEMBER_STATUS_ACTIVE)
            )

            assertThat(result1.members).containsExactlyInAnyOrder(aliceResult, bobResult)

            val result2 = memberLookupRestResource.lookup(
                HOLDING_IDENTITY_STRING,
                statuses = listOf(MEMBER_STATUS_SUSPENDED)
            )

            assertThat(result2.members).isEmpty()

            val result3 = memberLookupRestResource.lookup(
                HOLDING_IDENTITY_STRING,
                statuses = listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)
            )

            assertThat(result3.members).containsExactlyInAnyOrder(aliceResult, bobResult)
        }

        @Test
        fun `lookup by MGM filtered by statuses returns a list of members and their contexts`() {
            whenever(groupReader.lookup(MembershipStatusFilter.ACTIVE_OR_SUSPENDED))
                .doReturn(setOf(mgm, alice, bob, charlie))

            val result1 = memberLookupRestResource.lookup(
                MGM_HOLDING_IDENTITY_STRING,
                statuses = listOf(MEMBER_STATUS_ACTIVE)
            )

            assertThat(result1.members).containsExactlyInAnyOrder(aliceResult, bobResult, mgmResult)

            val result2 = memberLookupRestResource.lookup(
                MGM_HOLDING_IDENTITY_STRING,
                statuses = listOf(MEMBER_STATUS_SUSPENDED)
            )

            assertThat(result2.members).containsExactlyInAnyOrder(charlieResult)

            val result3 = memberLookupRestResource.lookup(
                MGM_HOLDING_IDENTITY_STRING,
                statuses = listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)
            )

            assertThat(result3.members).containsExactlyInAnyOrder(aliceResult, bobResult, charlieResult, mgmResult)
        }

        @Test
        fun `lookup filtered by statuses is case-insensitive`() {
            val result1 = memberLookupRestResource.lookup(
                HOLDING_IDENTITY_STRING,
                statuses = listOf(MEMBER_STATUS_ACTIVE.lowercase())
            )

            assertThat(result1.members).containsExactlyInAnyOrder(aliceResult, bobResult)
        }

        @Test
        fun `lookup should fail when invalid statuses are used`() {
            val exception = assertThrows<ResourceNotFoundException> {
                memberLookupRestResource.lookup(
                    HOLDING_IDENTITY_STRING,
                    statuses = listOf("invalid-status")
                )
            }
            assertThat(exception).hasMessageContaining("Invalid status")
        }

        @Test
        fun `lookup should fail when non-existent holding identity is used`() {
            val ex =
                assertThrows<ResourceNotFoundException> { memberLookupRestResource.lookup(BAD_HOLDING_IDENTITY.value) }
            assertThat(ex).hasMessageContaining("Could not find holding identity")
        }
    }

    @Nested
    inner class ViewGroupParametersTests {

        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `viewGroupParameters should fail when non-existent holding identity is used`() {
            val ex = assertThrows<ResourceNotFoundException> {
                memberLookupRestResource.viewGroupParameters(BAD_HOLDING_IDENTITY.value)
            }
            assertThat(ex).hasMessageContaining("Could not find holding identity")
        }

        @Test
        fun `viewGroupParameters fails with bad request if short hash is invalid`() {
            assertThrows<BadRequestException> {
                memberLookupRestResource.viewGroupParameters("INVALID_SHORT_HASH")
            }
        }

        @Test
        fun `viewGroupParameters fails when reader returns null`() {
            whenever(groupReader.groupParameters).doReturn(null)
            val ex = assertThrows<ResourceNotFoundException> {
                memberLookupRestResource.viewGroupParameters(HOLDING_IDENTITY_STRING)
            }
            assertThat(ex).hasMessageContaining("Could not find group parameters")
        }

        @Test
        fun `viewGroupParameters correctly returns group parameters as JSON string`() {
            val expectedGroupParamsMap = mapOf(
                MPV_KEY to "1",
                EPOCH_KEY to "1",
                MODIFIED_TIME_KEY to clock.instant().toString(),
            )

            val result = memberLookupRestResource.viewGroupParameters(HOLDING_IDENTITY_STRING)

            assertThat(result).containsExactlyEntriesOf(expectedGroupParamsMap)
        }
    }
}
