package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.event.MembershipEvent
import net.corda.data.membership.event.registration.MgmOnboarded
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.schema.Schemas.Membership.EVENT_TOPIC
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.util.UUID

class MGMRegistrationOutputPublisherTest {

    private val holdingIdentity = HoldingIdentity(
        MemberX500Name.parse("O=Alice, L=London, C=GB"),
        UUID(0, 1).toString()
    )
    private val memberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn holdingIdentity.groupId
    }
    private val mgmContext: MGMContext = mock()
    private val memberInfo: MemberInfo = mock {
        on { mgmProvidedContext } doReturn mgmContext
        on { memberProvidedContext } doReturn memberContext
        on { name } doReturn holdingIdentity.x500Name
    }
    private val mgmRegistrationOutputPublisher = MGMRegistrationOutputPublisher()

    @Test
    fun `Publish runs successfully`() {
        val publishedRecords = mgmRegistrationOutputPublisher.createRecords(memberInfo)

        assertThat(publishedRecords.map { it.topic }).containsExactlyInAnyOrder(
            MEMBER_LIST_TOPIC,
            EVENT_TOPIC
        )

        val shortHash = holdingIdentity.shortHash.value
        publishedRecords.firstOrNull {
            it.topic == EVENT_TOPIC
        }?.let {
            assertThat(it.key)
                .isNotNull
                .isEqualTo(shortHash)
            assertThat(it.value).isInstanceOf(MembershipEvent::class.java)
            val event = it.value as MembershipEvent
            assertThat(event.event).isInstanceOf(MgmOnboarded::class.java)
            val onboardedEvent = event.event as MgmOnboarded
            val onboardedMgm = onboardedEvent.onboardedMgm
            assertThat(
                MemberX500Name.parse(onboardedMgm.x500Name)
            ).isEqualTo(holdingIdentity.x500Name)
            assertThat(
                onboardedMgm.groupId
            ).isEqualTo(holdingIdentity.groupId)
        }

        publishedRecords.firstOrNull {
            it.topic == MEMBER_LIST_TOPIC
        }?.let {
            assertThat(it.key)
                .isNotNull
                .isEqualTo("$shortHash-$shortHash")
            assertThat(it.value).isInstanceOf(PersistentMemberInfo::class.java)
            val memberInfo = it.value as PersistentMemberInfo
            val avroHoldingId = memberInfo.viewOwningMember
            assertThat(
                MemberX500Name.parse(avroHoldingId.x500Name)
            ).isEqualTo(holdingIdentity.x500Name)
            assertThat(
                avroHoldingId.groupId
            ).isEqualTo(holdingIdentity.groupId)
        }
    }
}