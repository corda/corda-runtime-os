package net.corda.e2etest.utilities.types

import net.corda.e2etest.utilities.ClusterInfo

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
    val clusterInfo: ClusterInfo
)