package net.corda.membership.lib.impl.approval

import net.corda.membership.lib.approval.RegistrationRule
import net.corda.membership.lib.approval.RegistrationRulesEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RegistrationRulesEngineTest {

    private companion object {
        const val MEMBER_KEY = "member"
        const val DIFF_KEY = "diff"
        const val ADDED_KEY = "added"
        const val REMOVED_KEY = "removed"
        const val VALUE = "value"
        const val CHANGED_VALUE = "changed"
    }

    private val rule = mock<RegistrationRule>()
    private val knownFalseRule = mock<RegistrationRule> {
        on { evaluate(any()) } doReturn false
    }
    private val activeMemberInfo = mapOf(MEMBER_KEY to VALUE, DIFF_KEY to VALUE, REMOVED_KEY to VALUE)
    private val proposedMemberInfo = mapOf(MEMBER_KEY to VALUE, DIFF_KEY to CHANGED_VALUE, ADDED_KEY to VALUE)
    private val expectedDiff = setOf(DIFF_KEY, ADDED_KEY, REMOVED_KEY)

    private val rulesEngine = RegistrationRulesEngine.Impl(listOf(rule, knownFalseRule))

    @Test
    fun `returns true if any rule evaluates to true`() {
        whenever(rule.evaluate(expectedDiff)).doReturn(true)

        assertThat(
            rulesEngine.requiresManualApproval(proposedMemberInfo, activeMemberInfo)
        ).isTrue
    }

    @Test
    fun `returns false if no rule evaluates to true`() {
        whenever(rule.evaluate(expectedDiff)).doReturn(false)

        assertThat(
            rulesEngine.requiresManualApproval(proposedMemberInfo, activeMemberInfo)
        ).isFalse
    }
}
