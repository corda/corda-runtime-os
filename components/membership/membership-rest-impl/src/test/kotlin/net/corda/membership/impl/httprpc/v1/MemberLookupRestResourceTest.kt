package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.httprpc.v1.types.response.RestMemberInfo
import net.corda.membership.httprpc.v1.types.response.RestMemberInfoList
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
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
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.httprpc.exception.BadRequestException
import net.corda.membership.impl.rest.v1.MemberLookupRestResourceImpl
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith

class MemberLookupRestResourceTest {
    companion object {
        private const val KNOWN_KEY = "12345"
        private val HOLDING_IDENTITY_STRING = "1234567890ab"
        private val BAD_HOLDING_IDENTITY = ShortHash.of("deaddeaddead")
        private val clock = TestClock(Instant.ofEpochSecond(100))
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
    private val memberInfoList = listOf(alice, bob)

    private val aliceRestResult = RestMemberInfoList(
        listOf(
            RestMemberInfo(
                alice.memberProvidedContext.entries.associate { it.key to it.value },
                alice.mgmProvidedContext.entries.associate { it.key to it.value }
            )
        )
    )
    private val bobRestResult = RestMemberInfoList(
        listOf(
            RestMemberInfo(
                bob.memberProvidedContext.entries.associate { it.key to it.value },
                bob.mgmProvidedContext.entries.associate { it.key to it.value }
            )
        )
    )

    @Suppress("SpreadOperator")
    private fun createMemberInfo(name: String): MemberInfo = memberInfoFactory.create(
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
            STATUS to MEMBER_STATUS_ACTIVE,
            MODIFIED_TIME to clock.instant().toString(),
            SERIAL to "1",
        )
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
        on { lookup() } doReturn memberInfoList
        on { groupParameters } doReturn mockGroupParameters
    }

    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(ShortHash.of(HOLDING_IDENTITY_STRING)) } doReturn VirtualNodeInfo(
            holdingIdentity,
            CpiIdentifier("test", "test", SecureHash("algorithm", "1234".toByteArray())),
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
        assertTrue(memberLookupRestResource.isRunning)
        memberLookupRestResource.stop()
        assertFalse(memberLookupRestResource.isRunning)
    }

    @Test
    fun `exception should be thrown when service is not running`() {
        val ex =
            assertFailsWith<ServiceUnavailableException> { memberLookupRestResource.lookup(BAD_HOLDING_IDENTITY.value) }
        assertTrue(ex.message.contains("MemberLookupRestResourceImpl"))
    }

    @Nested
    inner class LookupTests {

        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `unfiltered lookup returns a list of all active members and their contexts`() {
            val result = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING)
            assertEquals(2, result.members.size)
            assertEquals(RestMemberInfoList(memberInfoList.map {
                RestMemberInfo(it.memberProvidedContext.entries.associate { it.key to it.value },
                    it.mgmProvidedContext.entries.associate { it.key to it.value })
            }), result)
        }

        @Test
        fun `lookup filtered by common name (CN) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, commonName = "bob")
            assertEquals(1, result1.members.size)
            assertEquals(bobRestResult, result1)
            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, commonName = "BOB")
            assertEquals(1, result2.members.size)
            assertEquals(bobRestResult, result2)
        }

        @Test
        fun `lookup filtered by organization (O) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, organization = "ALICE")
            assertEquals(1, result1.members.size)
            assertEquals(aliceRestResult, result1)
            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, organization = "alice")
            assertEquals(1, result2.members.size)
            assertEquals(aliceRestResult, result2)
        }

        @Test
        fun `lookup filtered by organization unit (OU) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, organizationUnit = "unit2")
            assertEquals(1, result1.members.size)
            assertEquals(bobRestResult, result1)
            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, organizationUnit = "UNIT2")
            assertEquals(1, result2.members.size)
            assertEquals(bobRestResult, result2)
        }

        @Test
        fun `lookup filtered by locality (L) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, locality = "london")
            assertEquals(1, result1.members.size)
            assertEquals(aliceRestResult, result1)
            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, locality = "LONDON")
            assertEquals(1, result2.members.size)
            assertEquals(aliceRestResult, result2)
        }

        @Test
        fun `lookup filtered by state (ST) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, state = "state2")
            assertEquals(1, result1.members.size)
            assertEquals(bobRestResult, result1)
            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, state = "state2")
            assertEquals(1, result2.members.size)
            assertEquals(bobRestResult, result2)
        }

        @Test
        fun `lookup filtered by country (C) is case-insensitive and returns a list of members and their contexts`() {
            val result1 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, country = "gb")
            assertEquals(1, result1.members.size)
            assertEquals(aliceRestResult, result1)
            val result2 = memberLookupRestResource.lookup(HOLDING_IDENTITY_STRING, country = "GB")
            assertEquals(1, result2.members.size)
            assertEquals(aliceRestResult, result2)
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
            assertEquals(1, result1.members.size)
            assertEquals(bobRestResult, result1)
            val result2 = memberLookupRestResource.lookup(
                HOLDING_IDENTITY_STRING,
                "BOB",
                "BOB",
                "UNIT2",
                "DUBLIN",
                "STATE2",
                "IE"
            )
            assertEquals(1, result2.members.size)
            assertEquals(bobRestResult, result2)
        }

        @Test
        fun `lookup should fail when non-existent holding identity is used`() {
            val ex =
                assertFailsWith<ResourceNotFoundException> { memberLookupRestResource.lookup(BAD_HOLDING_IDENTITY.value) }
            assertTrue(ex.message.contains("holding identity"))
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
            val ex = assertFailsWith<ResourceNotFoundException> {
                memberLookupRestResource.viewGroupParameters(BAD_HOLDING_IDENTITY.value)
            }
            assertTrue(ex.message.contains("Could not find holding identity"))
        }

        @Test
        fun `viewGroupParameters fails with bad request if short hash is invalid`() {
            assertFailsWith<BadRequestException> {
                memberLookupRestResource.viewGroupParameters("INVALID_SHORT_HASH")
            }
        }

        @Test
        fun `viewGroupParameters fails when reader returns null`() {
            whenever(groupReader.groupParameters).doReturn(null)
            val ex = assertFailsWith<ResourceNotFoundException> {
                memberLookupRestResource.viewGroupParameters(HOLDING_IDENTITY_STRING)
            }
            assertTrue(ex.message.contains("Could not find group parameters"))
        }

        @Test
        fun `viewGroupParameters correctly returns group parameters as JSON string`() {
            val result = memberLookupRestResource.viewGroupParameters(HOLDING_IDENTITY_STRING)
            assertThat(result)
                .contains("\"${MPV_KEY}\":\"1\"")
                .contains("\"${EPOCH_KEY}\":\"1\"")
                .contains("\"${MODIFIED_TIME_KEY}\":\"${clock.instant()}\"")
        }
    }
}
