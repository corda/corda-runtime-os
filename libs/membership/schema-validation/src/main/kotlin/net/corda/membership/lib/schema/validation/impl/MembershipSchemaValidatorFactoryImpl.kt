package net.corda.membership.lib.schema.validation.impl;

import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.schema.membership.provider.MembershipSchemaProviderFactory
import org.osgi.service.component.annotations.Component

@Component(service = [MembershipSchemaValidatorFactory::class])
class MembershipSchemaValidatorFactoryImpl : MembershipSchemaValidatorFactory {
    override fun createValidator() = MembershipSchemaValidatorImpl(
        MembershipSchemaProviderFactory.getSchemaProvider()
    )
}
