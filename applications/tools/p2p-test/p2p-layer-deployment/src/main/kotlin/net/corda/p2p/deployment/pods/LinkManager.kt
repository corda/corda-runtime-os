package net.corda.p2p.deployment.pods

class LinkManager(
    index: Int,
    kafkaServers: String,
    details: P2PDeploymentDetails,
) : P2pPod(kafkaServers, index, details) {
    companion object {
        fun linkManagers(
            kafkaServers: String,
            details: P2PDeploymentDetails,
        ) = (1..details.linkManagerCount).map {
            LinkManager(it, kafkaServers, details)
        }
    }

    override val imageName = "p2p-link-manager"

    override val readyLog = ".*Waiting for link manager to start.*".toRegex()
}
