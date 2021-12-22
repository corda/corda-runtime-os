package net.corda.rpc.schema

class Schema {
    companion object {
        // RPC Permissions
        const val RPC_PERM_MGMT_REQ_TOPIC = "rpc.permissions.management"
        const val RPC_PERM_MGMT_RESP_TOPIC = "rpc.permissions.management.resp"
        const val RPC_PERM_USER_TOPIC = "rpc.permissions.user"
        const val RPC_PERM_GROUP_TOPIC = "rpc.permissions.group"
        const val RPC_PERM_ROLE_TOPIC = "rpc.permissions.role"
        const val RPC_PERM_ENTITY_TOPIC = "rpc.permissions.permission"
    }
}