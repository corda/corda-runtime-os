package net.corda.membership.impl

import net.corda.membership.GroupPolicy

class GroupPolicyExtension {
    companion object {
        /** Key name for group ID property. */
        const val GROUP_ID = "groupId"
        /** Key name for static network. */
        const val STATIC_NETWORK = "staticNetwork"
        /** Key name for static mgm in the static network. */
        const val STATIC_MGM = "mgm"
        /** Key name for mgm's private key alias for static network creation. */
        const val MGM_KEY_ALIAS = "keyAlias"

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        private val GroupPolicy.staticNetwork: Map<String, Any>
            get() = get(STATIC_NETWORK) as? Map<String, Any>
                ?: throw IllegalStateException("Error while retrieving static network from group policy JSON.")

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        private val GroupPolicy.staticMgm: Map<String, Any>
            get() = if(staticNetwork.containsKey(STATIC_MGM)) {
                staticNetwork.get(STATIC_MGM) as? Map<String, Any>
                    ?: throw IllegalStateException("Error while retrieving static mgm from group policy JSON.")
            } else {
                emptyMap()
            }

        @JvmStatic
        val GroupPolicy.mgmKeyAlias: String?
            get() = if(staticMgm.containsKey(MGM_KEY_ALIAS)) {
                staticMgm.get(MGM_KEY_ALIAS).toString()
            } else {
                null
            }
    }
}
