package net.corda.membership.lib.group.policy.validation.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.group.policy.validation.MembershipGroupPolicyValidator
import net.corda.membership.lib.group.policy.validation.MembershipInvalidGroupPolicyException
import net.corda.membership.lib.group.policy.validation.MembershipInvalidTlsTypeException
import net.corda.membership.lib.p2p.TlsType

internal class MembershipGroupPolicyValidatorImpl: MembershipGroupPolicyValidator {
    private val objectMapper = ObjectMapper()

    override fun validateGroupPolicy(
        groupPolicy: String,
        configurationGetService: ((String) -> SmartConfig?)
    ) {
        val groupPolicyJson = try {
            objectMapper.readTree(groupPolicy)
        } catch (ex: Exception) {
            throw MembershipInvalidGroupPolicyException("Could not parse the group policy: ${ex.message}", ex)
        }
        val groupPolicyTlsType = groupPolicyJson.get("p2pParameters")?.get("tlsType")?.asText()
        if (groupPolicyTlsType != null) {
            // groupPolicyTlsType will be null for MGM group policies
            val clusterTlsType = TlsType.getClusterType(configurationGetService)
            if (groupPolicyTlsType != clusterTlsType.groupPolicyName) {
                throw MembershipInvalidTlsTypeException(
                    "Group policy TLS type must be the same as the configuration of the cluster gateway",
                )
            }
        }
    }
}