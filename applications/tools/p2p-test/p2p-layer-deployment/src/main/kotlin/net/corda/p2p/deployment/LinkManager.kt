package net.corda.p2p.deployment

class LinkManager(
    index: Int,
    tag: String,
    kafkaServers: String,
) : P2pPod(kafkaServers, tag, index) {
    companion object {
        fun linkManagers(count: Int, kafkaServers: String, tag: String) = (1..count).map {
            LinkManager(it, tag, kafkaServers)
        }
    }

    override val imageName = "p2p-link-manager"
}
