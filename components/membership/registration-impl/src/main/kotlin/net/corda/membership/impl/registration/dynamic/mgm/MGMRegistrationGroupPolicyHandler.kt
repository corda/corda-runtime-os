package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.virtualnode.HoldingIdentity

internal class MGMRegistrationGroupPolicyHandler(
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
) {
    private companion object {
        const val GROUP_POLICY_VERSION = 1L
    }

    fun buildAndPersist(
        holdingIdentity: HoldingIdentity,
        context: Map<String, String>
    ): LayeredPropertyMap {
        val groupPolicyMap = context.filterKeys {
            it.startsWith(GROUP_POLICY_PREFIX_WITH_DOT)
        }.mapKeys {
            it.key.removePrefix(GROUP_POLICY_PREFIX_WITH_DOT)
        }
        val groupPolicy = layeredPropertyMapFactory.createMap(groupPolicyMap)
        val groupPolicyPersistenceResult = membershipPersistenceClient.persistGroupPolicy(
            holdingIdentity,
            groupPolicy,
            GROUP_POLICY_VERSION
        )
        if (groupPolicyPersistenceResult is MembershipPersistenceResult.Failure) {
            throw MGMRegistrationGroupPolicyHandlingException(
                "Registration failed, persistence error. Reason: ${groupPolicyPersistenceResult.errorMsg}"
            )
        }

        return groupPolicy
    }
}

internal class MGMRegistrationGroupPolicyHandlingException(
    val reason: String
) : CordaRuntimeException(reason)