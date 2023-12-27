package net.corda.p2p.linkmanager.membership

import net.corda.data.p2p.app.MembershipStatusFilter.ACTIVE_OR_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.utilities.Either
import net.corda.utilities.parseOrNull
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NetworkMessagingValidatorTest {

    data class TestConfig(val sender: HoldingIdentity, val receiver: HoldingIdentity, val canMessage: Boolean)

    companion object {
        private const val GROUP_ID = "group-id"

        private val nonMgmMgmProvidedContext: MGMContext = mock()
        private val mgmMgmProvidedContext: MGMContext = mock {
            on { parseOrNull<Boolean>(IS_MGM) } doReturn true
        }

        private val groupReader: MembershipGroupReader = mock()

        // Non-mgm member that is active
        private val activeMemberHoldingId = mockMember("active") {
            on { isActive } doReturn true
            on { mgmProvidedContext } doReturn nonMgmMgmProvidedContext
        }

        // Non-mgm member that is inactive (suspended/pending)
        private val inactiveMemberHoldingId = mockMember("inactive") {
            on { isActive } doReturn false
            on { mgmProvidedContext } doReturn nonMgmMgmProvidedContext
        }

        // member not visible in the member list as active or suspended either because they are pending
        // or not yet registered
        private val registeringMemberHoldingId = mockMember("registering")

        // MGM member
        private val mgmHoldingId = mockMember("mgm") {
            on { isActive } doReturn true
            on { mgmProvidedContext } doReturn mgmMgmProvidedContext
        }

        @JvmStatic
        fun allowedMessagingGroups() = listOf(
            TestConfig(activeMemberHoldingId, activeMemberHoldingId, true),
            TestConfig(activeMemberHoldingId, inactiveMemberHoldingId, false),
            TestConfig(activeMemberHoldingId, registeringMemberHoldingId, false),
            TestConfig(activeMemberHoldingId, mgmHoldingId, true),
            TestConfig(inactiveMemberHoldingId, activeMemberHoldingId, false),
            TestConfig(inactiveMemberHoldingId, inactiveMemberHoldingId, false),
            TestConfig(inactiveMemberHoldingId, registeringMemberHoldingId, false),
            TestConfig(inactiveMemberHoldingId, mgmHoldingId, true),
            TestConfig(registeringMemberHoldingId, activeMemberHoldingId, false),
            TestConfig(registeringMemberHoldingId, inactiveMemberHoldingId, false),
            TestConfig(registeringMemberHoldingId, registeringMemberHoldingId, false),
            TestConfig(registeringMemberHoldingId, mgmHoldingId, true),
            TestConfig(mgmHoldingId, activeMemberHoldingId, true),
            TestConfig(mgmHoldingId, inactiveMemberHoldingId, true),
            TestConfig(mgmHoldingId, registeringMemberHoldingId, true),
            TestConfig(mgmHoldingId, mgmHoldingId, true),
        )

        private fun mockMember(
            orgName: String,
            memberInfoStubbing: (KStubbing<MemberInfo>.(MemberInfo) -> Unit)? = null
        ): HoldingIdentity {
            val memberName = MemberX500Name.parse("O=$orgName-member, L=London, C=GB")
            val info = memberInfoStubbing?.let { mock(stubbing = it) }
            whenever(groupReader.lookup(memberName, ACTIVE_OR_SUSPENDED)).doReturn(info)
            return HoldingIdentity(memberName, GROUP_ID)
        }
    }

    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val networkMessagingValidator = NetworkMessagingValidator(membershipGroupReaderProvider)

    @ParameterizedTest
    @MethodSource("allowedMessagingGroups")
    fun `validate inbound performs as expected`(testConfig: TestConfig) {
        val result = networkMessagingValidator.validateInbound(testConfig.sender, testConfig.receiver)

        if (testConfig.canMessage) {
            assertThat(result).isInstanceOf(Either.Left::class.java)
        } else {
            assertThat(result).isInstanceOf(Either.Right::class.java)
        }
    }

    @ParameterizedTest
    @MethodSource("allowedMessagingGroups")
    fun `validate outbound performs as expected`(testConfig: TestConfig) {
        val result = networkMessagingValidator.validateOutbound(testConfig.sender, testConfig.receiver)

        if (testConfig.canMessage) {
            assertThat(result).isInstanceOf(Either.Left::class.java)
        } else {
            assertThat(result).isInstanceOf(Either.Right::class.java)
        }
    }

    @ParameterizedTest
    @MethodSource("allowedMessagingGroups")
    fun `isValidInbound performs as expected`(testConfig: TestConfig) {
        val valid = networkMessagingValidator.isValidInbound(testConfig.sender, testConfig.receiver)

        assertThat(valid).isEqualTo(testConfig.canMessage)
    }
}