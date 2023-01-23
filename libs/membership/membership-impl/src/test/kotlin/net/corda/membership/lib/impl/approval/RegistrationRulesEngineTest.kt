package net.corda.membership.lib.impl.approval

import net.corda.membership.lib.approval.RegistrationRule
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RegistrationRulesEngineTest {

    private companion object {
        const val MEMBER_KEY = "member"
        const val DIFF_KEY = "diff"
    }

    private val rule = mock<RegistrationRule>()
    private val knownFalseRule = mock<RegistrationRule> {
        on { evaluate(any()) } doReturn false
    }
    private val memberContext1 = mock<MemberContext> {
        on { entries } doReturn mapOf(MEMBER_KEY to MEMBER_KEY).entries
    }
    private val memberContext2 = mock<MemberContext> {
        on { entries } doReturn mapOf(MEMBER_KEY to MEMBER_KEY, DIFF_KEY to DIFF_KEY).entries
    }
    private val activeMemberInfo = mock<MemberInfo> {
        on { memberProvidedContext } doReturn memberContext1
    }
    private val proposedMemberInfo = mock<MemberInfo> {
        on { memberProvidedContext } doReturn memberContext2
    }
    private val rulesEngine = RegistrationRulesEngineImpl(listOf(rule, knownFalseRule))

    @Test
    fun `returns true if any rule evaluates to true`() {
        whenever(rule.evaluate(any())).doReturn(true)

        assertThat(
            rulesEngine.requiresManualApproval(proposedMemberInfo, activeMemberInfo)
        ).isTrue
    }

    @Test
    fun `returns false if no rule evaluates to true`() {
        whenever(rule.evaluate(any())).doReturn(false)

        assertThat(
            rulesEngine.requiresManualApproval(proposedMemberInfo, activeMemberInfo)
        ).isFalse
    }

    @Test
    fun `correctly computes MemberInfo difference`() {
        val capturedDiff = argumentCaptor<Collection<String>>()
        whenever(rule.evaluate(capturedDiff.capture())).doReturn(true)

        RegistrationRulesEngineImpl(listOf(rule)).requiresManualApproval(proposedMemberInfo, activeMemberInfo)

        with(capturedDiff.firstValue) {
            assertThat(size).isEqualTo(1)
            assertThat(containsAll(listOf(DIFF_KEY)))
        }
    }

    @Test
    fun `if active MemberInfo is null, MemberInfo difference equals proposed MemberInfo`() {
        val capturedDiff = argumentCaptor<Collection<String>>()
        whenever(rule.evaluate(capturedDiff.capture())).doReturn(true)

        RegistrationRulesEngineImpl(listOf(rule)).requiresManualApproval(proposedMemberInfo, null)

        with(capturedDiff.firstValue) {
            assertThat(size).isEqualTo(2)
            assertThat(containsAll(listOf(MEMBER_KEY, DIFF_KEY)))
        }
    }
}
