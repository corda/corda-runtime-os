package net.corda.membership.lib.group.policy.validation.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MembershipGroupPolicyValidatorFactoryImplTest {
    private val impl = MembershipGroupPolicyValidatorFactoryImpl()
    @Test
    fun `createValidator creates a new instance of validator`() {
        assertThat(impl.createValidator())
            .isInstanceOf(MembershipGroupPolicyValidatorImpl::class.java)
    }
}