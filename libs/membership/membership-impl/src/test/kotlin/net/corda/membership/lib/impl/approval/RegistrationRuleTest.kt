package net.corda.membership.lib.impl.approval

import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegistrationRuleTest {

    private companion object {
        const val REGEX_STRING = "corda.session.*"
    }

    @Test
    fun `evaluates to true if one or more input keys matches regex`() {
        assertThat(
            RegistrationRuleImpl(REGEX_STRING.toRegex()).evaluate(listOf(PARTY_NAME, PARTY_SESSION_KEY))
        ).isTrue
    }

    @Test
    fun `evaluates to false if none of the input keys matches regex`() {
        assertThat(
            RegistrationRuleImpl(REGEX_STRING.toRegex()).evaluate(listOf(PARTY_NAME, GROUP_ID))
        ).isFalse
    }

}
