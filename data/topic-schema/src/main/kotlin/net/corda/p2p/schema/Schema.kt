package net.corda.p2p.schema

class Schema {
    companion object {
        const val P2P_OUT_TOPIC = "p2p.out"
        const val P2P_IN_TOPIC = "p2p.in"
        const val P2P_OUT_MARKERS = "p2p.out.markers"
        const val LINK_OUT_TOPIC = "link.out"
        const val LINK_IN_TOPIC = "link.in"
        const val SESSION_OUT_PARTITIONS = "session.out.partitions"
    }
}