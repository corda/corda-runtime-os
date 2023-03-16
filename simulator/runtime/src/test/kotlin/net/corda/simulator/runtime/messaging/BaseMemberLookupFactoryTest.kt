package net.corda.simulator.runtime.messaging

import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BaseMemberLookupFactoryTest {

    @Test
    fun `should create a MemberLookup which knows about the member for who it was created`() {

        // Given a registry knows about one member
        val member = MemberX500Name.parse("O=Alice, L=London, C=GB")

        val memberRegistry = mock<HasMemberInfos>()
        val mapItem = member to object : MemberInfo by mock() {
            override fun getName(): MemberX500Name = member
        }
        whenever(memberRegistry.members).thenReturn(mapOf(mapItem))

        val ml = BaseMemberLookupFactory().createMemberLookup(member, memberRegistry)

        assertThat(ml.myInfo().name, `is`(member))
    }

    @Test
    fun `should know about other members which the fiber knows about`() {

        // Given some members infos which a registry will return
        val members = listOf("Alice", "Bob", "Charlie").map {
            MemberX500Name.parse("O=$it, L=London, C=GB")
        }
        val memberRegistry = mock<HasMemberInfos>()
        val mapItems = members.associateWith {
            object : MemberInfo by mock() {
                override fun getName() = it
            }
        }
        whenever(memberRegistry.members).thenReturn(mapItems)

        // When we create a membership lookup and use it to find the members
        val ml = BaseMemberLookupFactory().createMemberLookup(members[0], memberRegistry)
        val foundMembers = ml.lookup()

        assertThat(foundMembers.map {it.name}.sorted(), `is`(members))
    }

    @Test
    fun `should be able to look up members by their public key`() {

        // Given some members infos with keys
        val members = listOf("Alice", "Bob", "Charlie").map {
            MemberX500Name.parse("O=$it, L=London, C=GB")
        }
        val keys = members.associateWith { generateKeys(3) }

        val (alice, bob, _) = members
        val memberRegistry = mock<HasMemberInfos>()
        val mapItems = members.associateWith {
            val memberInfo = mock<MemberInfo>()
            whenever(memberInfo.name).thenReturn(it)
            whenever(memberInfo.ledgerKeys).thenReturn(keys[it]!!)
            memberInfo
        }
        whenever(memberRegistry.members).thenReturn(mapItems)
        val bobsKey = mapItems[bob]?.ledgerKeys?.get(2) ?: fail("Couldn't find Bob, should never happen")

        // When we create a membership lookup and use it to find a member by key
        val ml = BaseMemberLookupFactory().createMemberLookup(alice, memberRegistry)
        val member = ml.lookup(bobsKey)

        // Then it should have found the member
        assertThat(member?.name, `is`(bob))
    }
}
