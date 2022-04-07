package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.libs.packaging.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfo
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfoList
import net.corda.membership.impl.EndpointInfoImpl
import net.corda.membership.impl.MGMContextImpl
import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.MemberInfoExtension
import net.corda.membership.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.impl.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.impl.MemberInfoImpl
import net.corda.membership.impl.converter.EndpointInfoConverter
import net.corda.membership.impl.converter.PublicKeyConverter
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith

class MemberLookupRpcOpsTest {
    companion object {
        private const val KNOWN_KEY = "12345"
        private const val HOLDING_IDENTITY_STRING = "test"
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

    private val endpoints = listOf(
        EndpointInfoImpl("https://corda5.r3.com:10000", EndpointInfo.DEFAULT_PROTOCOL_VERSION),
        EndpointInfoImpl("https://corda5.r3.com:10001", 10)
    )

    private val holdingIdentity = HoldingIdentity("test", "0")

    private val keyEncodingService: CipherSchemeMetadata = mock {
        on { decodePublicKey(KNOWN_KEY) } doReturn knownKey
        on { encodeAsString(knownKey) } doReturn KNOWN_KEY
    }

    private val converters = listOf(
        EndpointInfoConverter(),
        PublicKeyConverter(keyEncodingService)
    )

    private val layeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory(converters)

    private val alice = createMemberInfo("CN=Alice,O=Alice,OU=Unit1,L=London,ST=State1,C=GB")
    private val bob = createMemberInfo("CN=Bob,O=Bob,OU=Unit2,L=Dublin,ST=State2,C=IE")
    private val memberInfoList = listOf(alice, bob)

    private val aliceRpcResult = RpcMemberInfoList(
        listOf(
            RpcMemberInfo(
                alice.memberProvidedContext.entries.associate { it.key to it.value },
                alice.mgmProvidedContext.entries.associate { it.key to it.value }
            )
        )
    )
    private val bobRpcResult = RpcMemberInfoList(
        listOf(
            RpcMemberInfo(
                bob.memberProvidedContext.entries.associate { it.key to it.value },
                bob.mgmProvidedContext.entries.associate { it.key to it.value }
            )
        )
    )

    @Suppress("SpreadOperator")
    private fun createMemberInfo(name: String): MemberInfo = MemberInfoImpl(
        memberProvidedContext = layeredPropertyMapFactory.create<MemberContextImpl>(
            sortedMapOf(
                PARTY_NAME to name,
                PARTY_OWNING_KEY to KNOWN_KEY,
                GROUP_ID to "DEFAULT_MEMBER_GROUP_ID",
                *convertPublicKeys().toTypedArray(),
                *convertEndpoints().toTypedArray(),
                SOFTWARE_VERSION to "5.0.0",
                PLATFORM_VERSION to "10",
                SERIAL to "1"
            )
        ),
        mgmProvidedContext = layeredPropertyMapFactory.create<MGMContextImpl>(
            sortedMapOf(
                MemberInfoExtension.STATUS to MEMBER_STATUS_ACTIVE,
                MemberInfoExtension.MODIFIED_TIME to Instant.now().toString()
            )
        )
    )

    private fun convertPublicKeys(): List<Pair<String, String>> =
        keys.mapIndexed { index, identityKey ->
            String.format(
                MemberInfoExtension.IDENTITY_KEYS_KEY,
                index
            ) to keyEncodingService.encodeAsString(identityKey)
        }

    private fun convertEndpoints(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (index in endpoints.indices) {
            result.add(
                Pair(
                    String.format(MemberInfoExtension.URL_KEY, index),
                    endpoints[index].url
                )
            )
            result.add(
                Pair(
                    String.format(MemberInfoExtension.PROTOCOL_VERSION, index),
                    endpoints[index].protocolVersion.toString()
                )
            )
        }
        return result
    }

    private val groupReader: MembershipGroupReader = mock {
        on { lookup() } doReturn memberInfoList
    }

    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getById(HOLDING_IDENTITY_STRING) } doReturn VirtualNodeInfo(
            holdingIdentity,
            CpiIdentifier("test", "test", SecureHash("algorithm", "1234".toByteArray())),
            null, UUID.randomUUID(), null, UUID.randomUUID()
        )
    }

    private val memberLookupRpcOps = MemberLookupRpcOpsImpl(
        lifecycleCoordinatorFactory,
        membershipGroupReaderProvider,
        virtualNodeInfoReadService
    )

    @Test
    fun `starting and stopping the service succeeds`() {
        memberLookupRpcOps.start()
        assertTrue(memberLookupRpcOps.isRunning)
        memberLookupRpcOps.stop()
        assertFalse(memberLookupRpcOps.isRunning)
    }

    @Test
    fun `unfiltered lookup returns a list of all active members and their contexts`() {
        memberLookupRpcOps.start()
        memberLookupRpcOps.activate("")
        val result = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING)
        assertEquals(2, result.members.size)
        assertEquals(
            RpcMemberInfoList(
                memberInfoList.map {
                    RpcMemberInfo(
                        it.memberProvidedContext.entries.associate { it.key to it.value },
                        it.mgmProvidedContext.entries.associate { it.key to it.value }
                    )
                }
            ),
            result
        )
        memberLookupRpcOps.deactivate("")
        memberLookupRpcOps.stop()
    }

    @Test
    fun `lookup filtered by common name (CN) is case-insensitive and returns a list of members and their contexts`() {
        memberLookupRpcOps.start()
        memberLookupRpcOps.activate("")
        val result1 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, commonName = "bob")
        assertEquals(1, result1.members.size)
        assertEquals(bobRpcResult, result1)
        val result2 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, commonName = "BOB")
        assertEquals(1, result2.members.size)
        assertEquals(bobRpcResult, result2)
        memberLookupRpcOps.deactivate("")
        memberLookupRpcOps.stop()
    }

    @Test
    fun `lookup filtered by organisation (O) is case-insensitive and returns a list of members and their contexts`() {
        memberLookupRpcOps.start()
        memberLookupRpcOps.activate("")
        val result1 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, organisation = "ALICE")
        assertEquals(1, result1.members.size)
        assertEquals(aliceRpcResult, result1)
        val result2 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, organisation = "alice")
        assertEquals(1, result2.members.size)
        assertEquals(aliceRpcResult, result2)
        memberLookupRpcOps.deactivate("")
        memberLookupRpcOps.stop()
    }

    @Test
    fun `lookup filtered by organisation unit (OU) is case-insensitive and returns a list of members and their contexts`() {
        memberLookupRpcOps.start()
        memberLookupRpcOps.activate("")
        val result1 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, organisationUnit = "unit2")
        assertEquals(1, result1.members.size)
        assertEquals(bobRpcResult, result1)
        val result2 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, organisationUnit = "UNIT2")
        assertEquals(1, result2.members.size)
        assertEquals(bobRpcResult, result2)
        memberLookupRpcOps.deactivate("")
        memberLookupRpcOps.stop()
    }

    @Test
    fun `lookup filtered by locality (L) is case-insensitive and returns a list of members and their contexts`() {
        memberLookupRpcOps.start()
        memberLookupRpcOps.activate("")
        val result1 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, locality = "london")
        assertEquals(1, result1.members.size)
        assertEquals(aliceRpcResult, result1)
        val result2 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, locality = "LONDON")
        assertEquals(1, result2.members.size)
        assertEquals(aliceRpcResult, result2)
        memberLookupRpcOps.deactivate("")
        memberLookupRpcOps.stop()
    }

    @Test
    fun `lookup filtered by state (ST) is case-insensitive and returns a list of members and their contexts`() {
        memberLookupRpcOps.start()
        memberLookupRpcOps.activate("")
        val result1 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, state = "state2")
        assertEquals(1, result1.members.size)
        assertEquals(bobRpcResult, result1)
        val result2 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, state = "state2")
        assertEquals(1, result2.members.size)
        assertEquals(bobRpcResult, result2)
        memberLookupRpcOps.deactivate("")
        memberLookupRpcOps.stop()
    }

    @Test
    fun `lookup filtered by country (C) is case-insensitive and returns a list of members and their contexts`() {
        memberLookupRpcOps.start()
        memberLookupRpcOps.activate("")
        val result1 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, country = "gb")
        assertEquals(1, result1.members.size)
        assertEquals(aliceRpcResult, result1)
        val result2 = memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, country = "GB")
        assertEquals(1, result2.members.size)
        assertEquals(aliceRpcResult, result2)
        memberLookupRpcOps.deactivate("")
        memberLookupRpcOps.stop()
    }

    @Test
    fun `lookup filtered by all attributes is case-insensitive and returns a list of members and their contexts`() {
        memberLookupRpcOps.start()
        memberLookupRpcOps.activate("")
        val result1 =
            memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, "bob", "bob", "unit2", "dublin", "state2", "ie")
        assertEquals(1, result1.members.size)
        assertEquals(bobRpcResult, result1)
        val result2 =
            memberLookupRpcOps.lookup(HOLDING_IDENTITY_STRING, "BOB", "BOB", "UNIT2", "DUBLIN", "STATE2", "IE")
        assertEquals(1, result2.members.size)
        assertEquals(bobRpcResult, result2)
        memberLookupRpcOps.deactivate("")
        memberLookupRpcOps.stop()
    }

    @Test
    fun `lookup should fail when non-existent holding identity is used`() {
        memberLookupRpcOps.start()
        memberLookupRpcOps.activate("")
        val ex = assertFailsWith<ResourceNotFoundException> { memberLookupRpcOps.lookup("failingTest") }
        assertTrue(ex.message.contains("holding identity"))
        memberLookupRpcOps.deactivate("")
        memberLookupRpcOps.stop()
    }

    @Test
    fun `exception should be thrown when service is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> { memberLookupRpcOps.lookup("failingTest") }
        assertTrue(ex.message.contains("MemberLookupRpcOpsImpl"))
    }
}