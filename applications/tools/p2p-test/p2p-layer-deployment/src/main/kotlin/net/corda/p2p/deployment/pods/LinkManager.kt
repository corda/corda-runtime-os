package net.corda.p2p.deployment.pods

class LinkManager(
    index: Int,
    tag: String,
    kafkaServers: String,
    debug: Boolean,
    override val autoStart: Boolean,
) : P2pPod(kafkaServers, tag, index, debug) {
    companion object {
        fun linkManagers(
            count: Int,
            kafkaServers: String,
            tag: String,
            debug: Boolean,
            avoidStart: Boolean
        ) = (1..count).map {
            LinkManager(it, tag, kafkaServers, debug, !avoidStart)
        }
    }

    override val imageName = "p2p-link-manager"
}
