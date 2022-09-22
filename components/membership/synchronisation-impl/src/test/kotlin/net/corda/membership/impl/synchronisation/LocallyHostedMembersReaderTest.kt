package net.corda.membership.impl.synchronisation

import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class LocallyHostedMembersReaderTest {
    private val groupId = "GROUP_ID"
    private val alice = HoldingIdentity(
        MemberX500Name.parse("O=Alice, L=London, C=GB"),
        groupId
    )
    private val bob = HoldingIdentity(
        MemberX500Name.parse("O=Bob, L=London, C=GB"),
        groupId
    )
    private val carol = HoldingIdentity(
        MemberX500Name.parse("O=Carol, L=London, C=GB"),
        groupId
    )
    private val dean = HoldingIdentity(
        MemberX500Name.parse("O=Dean, L=London, C=GB"),
        groupId
    )
    private val mgm = HoldingIdentity(
        MemberX500Name.parse("O=Mgm, L=London, C=GB"),
        groupId
    )
    private val allMembers = listOf(
        alice,
        bob,
        carol,
        dean,
        mgm
    )
    private val members = allMembers.map { id ->
        mock<VirtualNodeInfo> {
            on { holdingIdentity } doReturn id
        }
    }
    private val membersInfo = allMembers.associateWith {
        it.mockMemberInfo()
    }
    private val mgmInfo = mgm.mockMemberInfo(true)
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getAll() } doReturn members
    }

    // Alice has no member
    private val aliceGroupReader = mock<MembershipGroupReader> {
        on { lookup() } doReturn emptyList()
    }

    // Bob knows nothing about himself
    private val bobGroupReader = mock<MembershipGroupReader> {
        on { lookup() } doReturn listOf(membersInfo[carol]!!, mgmInfo)
    }

    // Carol knows no MGM
    private val carolGroupReader = mock<MembershipGroupReader> {
        on { lookup() } doReturn listOf(membersInfo[carol]!!, membersInfo[bob]!!)
    }

    // Dean should know about himself and the MGM
    private val deanGroupReader = mock<MembershipGroupReader> {
        on { lookup() } doReturn listOf(membersInfo[dean]!!, membersInfo[bob]!!, mgmInfo)
    }
    private val mgmGroupReader = mock<MembershipGroupReader> {
        on { lookup() } doReturn listOf(membersInfo[dean]!!, membersInfo[bob]!!, mgmInfo)
    }
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(alice) } doReturn aliceGroupReader
        on { getGroupReader(bob) } doReturn bobGroupReader
        on { getGroupReader(carol) } doReturn carolGroupReader
        on { getGroupReader(dean) } doReturn deanGroupReader
        on { getGroupReader(mgm) } doReturn mgmGroupReader
    }

    private var reader = LocallyHostedMembersReader(
        virtualNodeInfoReadService,
        membershipGroupReaderProvider,
    )

    @Test
    fun `readAllLocalMembers returns all the members that have mgm and know about them self`() {
        val members = reader.readAllLocalMembers()

        assertThat(members).containsOnly(
            LocallyHostedMembersReader.LocallyHostedMember(
                member = dean,
                mgm = mgm,
            )
        )
    }

    private fun HoldingIdentity.mockMemberInfo(mgm: Boolean = false): MemberInfo {
        val mgmContext = mock<MGMContext> {
            on { parseOrNull(IS_MGM, Boolean::class.javaObjectType) } doReturn mgm
        }
        val memberContext = mock<MemberContext> {
            on { parse(MemberInfoExtension.GROUP_ID, String::class.java) } doReturn groupId
        }
        val memberName = this.x500Name
        return mock {
            on { name } doReturn memberName
            on { mgmProvidedContext } doReturn mgmContext
            on { memberProvidedContext } doReturn memberContext
        }
    }
}
