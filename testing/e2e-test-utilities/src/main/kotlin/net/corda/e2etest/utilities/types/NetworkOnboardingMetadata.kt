package net.corda.e2etest.utilities.types

import net.corda.e2etest.utilities.ClusterInfo
import net.corda.e2etest.utilities.exportGroupPolicy

/**
 * Metadata from the network onboarding process.
 *
 * @param holdingId the Holding identity short hash for the onboarded member.
 * @param x500Name the x500 name of the onboarded member.
 * @param registrationId the ID of the network registration performed during onboarding.
 * @param registrationContext the registration context submitted during onboarding.
 * @param clusterInfo the cluster where the onboarding was performed.
 */
data class NetworkOnboardingMetadata(
    val holdingId: String,
    val x500Name: String,
    val registrationId: String,
    val registrationContext: Map<String, String>,
    val clusterInfo: ClusterInfo,
) {
    fun getGroupPolicyFactory() =
        GroupPolicyFactory(
            holdingId = holdingId,
            clusterInfo = clusterInfo,
        )
}

/**
 * A group policy factory.
 *
 * @param holdingId The MGM member holding identity.
 * @param clusterInfo The MGM cluster.
 */
class GroupPolicyFactory(
    val holdingId: String,
    val clusterInfo: ClusterInfo,
) {

    /**
     * The group policy.
     */
    val groupPolicy by lazy {
        clusterInfo.exportGroupPolicy(holdingId)
    }
}
