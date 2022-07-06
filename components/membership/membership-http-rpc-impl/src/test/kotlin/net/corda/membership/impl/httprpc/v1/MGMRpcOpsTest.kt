package net.corda.membership.impl.httprpc.v1

import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.client.MGMOpsClient
import net.corda.membership.impl.EndpointInfoImpl
import net.corda.membership.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.impl.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.impl.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.impl.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.impl.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.impl.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.impl.MemberInfoFactoryImpl
import net.corda.membership.impl.converter.EndpointInfoConverter
import net.corda.membership.impl.converter.PublicKeyConverter
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import net.corda.test.util.time.TestClock
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.security.PublicKey
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

class MGMRpcOpsTest {
    companion object {
        private const val HOLDING_IDENTITY_ID = "DUMMY_ID"
        private val clock = TestClock(Instant.ofEpochSecond(100))
        private const val HOLDING_IDENTITY_STRING = "test"
        private const val KNOWN_KEY = "12345"

        enum class GroupPolicyType {
            STATIC,
            DYNAMIC
        }
    }

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
    }

    private val holdingIdentity = HoldingIdentity("test", "0")

    private val knownKey: PublicKey = mock()
    private val keys = listOf(knownKey, knownKey)

    private val keyEncodingService: CipherSchemeMetadata = mock {
        on { decodePublicKey(KNOWN_KEY) } doReturn knownKey
        on { encodeAsString(knownKey) } doReturn KNOWN_KEY
    }

    private val converters = listOf(
        EndpointInfoConverter(),
        PublicKeyConverter(keyEncodingService)
    )

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }

    private val endpoints = listOf(
        EndpointInfoImpl("https://corda5.r3.com:10000", EndpointInfo.DEFAULT_PROTOCOL_VERSION),
        EndpointInfoImpl("https://corda5.r3.com:10001", 10)
    )

    private val memberInfoFactory = MemberInfoFactoryImpl(LayeredPropertyMapMocks.createFactory(converters))

    private val alice = createMemberInfo("CN=Alice,O=Alice,OU=Unit1,L=London,ST=State1,C=GB","false")
    private val bob = createMemberInfo("CN=Bob,O=Bob,OU=Unit2,L=Dublin,ST=State2,C=IE","true")
    private val memberInfoList = listOf(alice, bob)

    private val mgmOpsClient: MGMOpsClient = mock {
        on { generateGroupPolicy(any()) } doReturn emptySet()
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
            null, UUID.randomUUID(), null, UUID.randomUUID(),
            timestamp = Instant.now()
        )
    }

    @Suppress("SpreadOperator")
    private fun createMemberInfo(name: String, isMGM : String): MemberInfo = memberInfoFactory.create(
        sortedMapOf(
            PARTY_NAME to name,
            PARTY_SESSION_KEY to KNOWN_KEY,
            GROUP_ID to "DEFAULT_MEMBER_GROUP_ID",
            *convertPublicKeys().toTypedArray(),
            *convertEndpoints().toTypedArray(),
            SOFTWARE_VERSION to "5.0.0",
            PLATFORM_VERSION to "10",
            SERIAL to "1"
        ),
        sortedMapOf(
            STATUS to MEMBER_STATUS_ACTIVE,
            MODIFIED_TIME to clock.instant().toString(),
            IS_MGM to isMGM
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

    private val mgmRpcOps = MGMRpcOpsImpl(
        lifecycleCoordinatorFactory,
        mgmOpsClient,
        membershipGroupReaderProvider,
        virtualNodeInfoReadService
    )

    @Test
    fun `starting and stopping the service succeeds`() {
        mgmRpcOps.start()
        assertTrue(mgmRpcOps.isRunning)
        mgmRpcOps.stop()
        assertFalse(mgmRpcOps.isRunning)
    }


    @Test
    fun `calling generate group policy calls the client svc`() {
        mgmRpcOps.start()
        mgmRpcOps.activate("")
        val result = mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_STRING)
        assertEquals(2, result.size)
//editing here

        mgmRpcOps.deactivate("")
        mgmRpcOps.stop()
    }

    private fun getSampleGroupPolicy(type: GroupPolicyType) =
    when (type) {
        GroupPolicyType.STATIC -> this::class.java.getResource("/SampleStaticGroupPolicy.json")!!.readText()
        GroupPolicyType.DYNAMIC -> this::class.java.getResource("/SampleDynamicGroupPolicy.json")!!.readText()
    }
}