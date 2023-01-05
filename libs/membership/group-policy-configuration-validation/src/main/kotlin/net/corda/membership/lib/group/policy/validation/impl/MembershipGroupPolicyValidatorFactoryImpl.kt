package net.corda.membership.lib.group.policy.validation.impl

import net.corda.membership.lib.group.policy.validation.MembershipGroupPolicyValidator
import net.corda.membership.lib.group.policy.validation.MembershipGroupPolicyValidatorFactory
import org.osgi.service.component.annotations.Component

@Component(service = [MembershipGroupPolicyValidatorFactory::class])
class MembershipGroupPolicyValidatorFactoryImpl: MembershipGroupPolicyValidatorFactory {
    override fun createValidator() : MembershipGroupPolicyValidator = MembershipGroupPolicyValidatorImpl()
}
