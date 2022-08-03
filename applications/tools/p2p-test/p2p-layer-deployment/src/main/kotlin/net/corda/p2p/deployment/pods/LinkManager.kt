package net.corda.p2p.deployment.pods

class LinkManager(
    kafkaServers: String,
    details: P2PDeploymentDetails,
) : P2pPod(kafkaServers, details, details.linkManagerCount) {
    companion object {
        fun linkManagers(
            kafkaServers: String,
            details: P2PDeploymentDetails,
        ) = listOf(LinkManager(kafkaServers, details))
    }

    override val imageName = "p2p-link-manager"

    override val readyLog = ".*LinkManager-1.* - Starting child.*".toRegex()
}
