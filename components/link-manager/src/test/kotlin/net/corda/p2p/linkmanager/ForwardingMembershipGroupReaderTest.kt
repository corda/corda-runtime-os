package net.corda.p2p.linkmanager

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseList
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class ForwardingMembershipGroupReaderTest {

    companion object {
        private const val GROUP_ID = "Group-Id"
        private val ALICE_X500_NAME = MemberX500Name.parse("O=Alice,C=GB,L=London")
        private val ALICE = createTestHoldingIdentity(ALICE_X500_NAME.toString(), GROUP_ID)
        private val BOB_X500_NAME = MemberX500Name.parse("O=Bob,C=GB,L=London")
        private val BOB = createTestHoldingIdentity(BOB_X500_NAME.toString(), GROUP_ID)
        private const val KEY_ALGORITHM = "EC"
        private const val BOB_ENDPOINT = "0.0.0.0"
        private val PUBLIC_KEY_HASH = "---- 32 Byte Public Key Hash----".toByteArray()
    }
    private val bobSessionKey = mock<PublicKey> {
        on { algorithm } doReturn KEY_ALGORITHM
    }
    private val bobEndpointInfo = mock<EndpointInfo> {
        on { url } doReturn BOB_ENDPOINT
        on { protocolVersion } doReturn EndpointInfo.DEFAULT_PROTOCOL_VERSION
    }
    private val bobMemberContext = mock<MemberContext> {
        on { parseList<EndpointInfo>("corda.endpoints") } doReturn listOf(bobEndpointInfo)
        on { parse<String>(MemberInfoExtension.GROUP_ID) } doReturn BOB.groupId
    }

    private var dependentChildren: Collection<LifecycleCoordinatorName> = mutableListOf()
    private var managedChildren: Collection<NamedLifecycle> = mutableListOf()

    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { _, context ->
        @Suppress("unchecked_cast")
        dependentChildren = context.arguments()[4] as Collection<LifecycleCoordinatorName>
        @Suppress("unchecked_cast")
        managedChildren = context.arguments()[5] as Collection<NamedLifecycle>
    }

    private val bobMemberInfo = mock<MemberInfo> {
        on { name } doReturn BOB.x500Name
        on { sessionInitiationKey } doReturn bobSessionKey
        on { memberProvidedContext } doReturn bobMemberContext
    }
    private val aliceGroupReader = mock<MembershipGroupReader>()
    private val mockMembershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(ALICE) } doReturn aliceGroupReader
    }
    private val forwardingMembershipGroupReader = ForwardingMembershipGroupReader(mockMembershipGroupReaderProvider, mock())

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
    }

    @Test
    fun `dependent and managed children are set properly`() {
        assertThat(dependentChildren).hasSize(1)
        assertThat(dependentChildren).containsExactlyInAnyOrder(
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
        )
        assertThat(managedChildren).hasSize(1)
        assertThat(managedChildren).containsExactlyInAnyOrder(
            NamedLifecycle(mockMembershipGroupReaderProvider, LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>())
        )
    }

    @Test
    fun `getMemberInfo by holding identity returns the correct MemberInfo`() {
        whenever(aliceGroupReader.lookup(BOB_X500_NAME)).thenReturn(bobMemberInfo)

        val memberInfo = forwardingMembershipGroupReader.getMemberInfo(ALICE, BOB)
        assertThat(memberInfo!!.holdingIdentity).isEqualTo(BOB)
        assertThat(memberInfo.sessionPublicKey).isEqualTo(bobSessionKey)
        assertThat(memberInfo.publicKeyAlgorithm).isEqualTo(KeyAlgorithm.ECDSA)
        assertThat(memberInfo.endPoint).isEqualTo(BOB_ENDPOINT)
    }

    @Test
    fun `getMemberInfo by public key hash returns the correct MemberInfo`() {
        whenever(aliceGroupReader.lookupBySessionKey(PublicKeyHash.Companion.parse(PUBLIC_KEY_HASH))).thenReturn(bobMemberInfo)

        val memberInfo = forwardingMembershipGroupReader.getMemberInfo(ALICE, PUBLIC_KEY_HASH)
        assertThat(memberInfo!!.holdingIdentity).isEqualTo(BOB)
        assertThat(memberInfo.sessionPublicKey).isEqualTo(bobSessionKey)
        assertThat(memberInfo.publicKeyAlgorithm).isEqualTo(KeyAlgorithm.ECDSA)
        assertThat(memberInfo.endPoint).isEqualTo(BOB_ENDPOINT)
    }

    @Test
    fun `getMemberInfo by holding identity returns null if lookup returns null`() {
        whenever(aliceGroupReader.lookup(BOB_X500_NAME)).thenReturn(null)

        assertThat(forwardingMembershipGroupReader.getMemberInfo(ALICE, BOB)).isNull()
    }

    @Test
    fun `getMemberInfo by public key hash returns null if lookup returns null`() {
        whenever(aliceGroupReader.lookupBySessionKey(PublicKeyHash.Companion.parse(PUBLIC_KEY_HASH))).thenReturn(null)

        assertThat(forwardingMembershipGroupReader.getMemberInfo(ALICE, PUBLIC_KEY_HASH)).isNull()
    }

    @Test
    fun `getMemberInfo by holding identity returns null if endpoint missing`() {
        whenever(aliceGroupReader.lookup(BOB_X500_NAME)).thenReturn(bobMemberInfo)
        val bobEndpointInfo = mock<EndpointInfo> {
            on { url } doReturn BOB_ENDPOINT
            on { protocolVersion } doReturn EndpointInfo.DEFAULT_PROTOCOL_VERSION + 1
        }
        val bobMemberContext = mock<MemberContext> {
            on { parseList<EndpointInfo>("corda.endpoints") } doReturn listOf(bobEndpointInfo)
        }
        whenever(bobMemberInfo.memberProvidedContext).thenReturn(bobMemberContext)

        assertThat(forwardingMembershipGroupReader.getMemberInfo(ALICE, BOB)).isNull()
    }

    @Test
    fun `getMemberInfo by public key hash returns null if endpoint missing`() {
        whenever(aliceGroupReader.lookupBySessionKey(PublicKeyHash.Companion.parse(PUBLIC_KEY_HASH))).thenReturn(bobMemberInfo)
        val bobEndpointInfo = mock<EndpointInfo> {
            on { url } doReturn BOB_ENDPOINT
            on { protocolVersion } doReturn EndpointInfo.DEFAULT_PROTOCOL_VERSION + 1
        }
        val bobMemberContext = mock<MemberContext> {
            on { parseList<EndpointInfo>("corda.endpoints") } doReturn listOf(bobEndpointInfo)
        }
        whenever(bobMemberInfo.memberProvidedContext).thenReturn(bobMemberContext)

        assertThat(forwardingMembershipGroupReader.getMemberInfo(ALICE, PUBLIC_KEY_HASH)).isNull()
    }

}