package net.corda.membership.lib.impl.approval

import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS
import net.corda.membership.lib.approval.RegistrationRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegistrationRuleTest {

    private companion object {
        const val REGEX_STRING = "corda.session.*"
    }

    @Test
    fun `evaluates to true if one or more input keys matches regex`() {
        assertThat(
            RegistrationRule.Impl(REGEX_STRING.toRegex()).evaluate(listOf(PARTY_NAME, PARTY_SESSION_KEYS))
        ).isTrue
    }

    @Test
    fun `evaluates to false if none of the input keys matches regex`() {
        assertThat(
            RegistrationRule.Impl(REGEX_STRING.toRegex()).evaluate(listOf(PARTY_NAME, GROUP_ID))
        ).isFalse
    }

}
