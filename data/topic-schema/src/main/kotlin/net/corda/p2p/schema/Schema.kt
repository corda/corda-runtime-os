package net.corda.p2p.schema

class Schema {
    companion object {
        const val P2P_OUT_TOPIC = "p2p.out"
        const val P2P_IN_TOPIC = "p2p.in"
        const val P2P_OUT_MARKERS = "p2p.out.markers"
        const val LINK_OUT_TOPIC = "link.out"
        const val LINK_IN_TOPIC = "link.in"
        const val SESSION_OUT_PARTITIONS = "session.out.partitions"

        // RPC Permissions
        const val RPC_PERM_MGMT_REQ_TOPIC = "rpc.permissions.management"
        const val RPC_PERM_MGMT_RESP_TOPIC = "rpc.permissions.management.resp"
        const val RPC_PERM_USER_TOPIC = "rpc.permissions.user"
        const val RPC_PERM_GROUP_TOPIC = "rpc.permissions.group"
        const val RPC_PERM_ROLE_TOPIC = "rpc.permissions.role"
    }
}