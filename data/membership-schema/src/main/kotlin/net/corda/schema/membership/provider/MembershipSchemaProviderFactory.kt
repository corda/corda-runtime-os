package net.corda.schema.membership.provider

import net.corda.schema.membership.provider.impl.MembershipSchemaProviderImpl

/**
 * Factory for membership schema providers.
 */
object MembershipSchemaProviderFactory {

    /**
     * Create a new membership schema provider.
     *
     * @return The new membership schema provider.
     */
    fun getSchemaProvider() : MembershipSchemaProvider {
        return MembershipSchemaProviderImpl()
    }
}