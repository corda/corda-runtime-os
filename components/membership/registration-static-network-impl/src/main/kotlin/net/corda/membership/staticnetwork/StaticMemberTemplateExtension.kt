package net.corda.membership.staticnetwork

import net.corda.membership.GroupPolicy

class StaticMemberTemplateExtension {
    companion object {
        /** Key name for static member template property. */
        const val STATIC_NETWORK_TEMPLATE = "staticNetwork"

        /** Key name for static mgm in the static network. */
        const val STATIC_MGM = "mgm"

        /** Key name for mgm's private key alias for static network creation. */
        const val MGM_KEY_ALIAS = "keyAlias"

        /** Key name for static members in the static network. */
        const val STATIC_MEMBERS = "members"

        /** Key name for member's name. */
        const val NAME = "name"

        /** Key name for key alias. */
        const val KEY_ALIAS = "keyAlias"

        /** Key name for historic key alias. */
        const val ROTATED_KEY_ALIAS = "rotatedKeyAlias-%s"

        /** Key name for member status. */
        const val MEMBER_STATUS = "memberStatus"

        /** Key name for endpoint URL. */
        const val ENDPOINT_URL = "endpointUrl-%s"

        /** Key name for endpoint protocol. */
        const val ENDPOINT_PROTOCOL = "endpointProtocol-%s"

        /** Key name for software version. */
        const val STATIC_SOFTWARE_VERSION = "softwareVersion"

        /** Key name for platform version. */
        const val STATIC_PLATFORM_VERSION = "platformVersion"

        /** Key name for serial number. */
        const val STATIC_SERIAL = "serial"

        /** Key name for modified time. */
        const val STATIC_MODIFIED_TIME = "modifiedTime"

        /** Static network template. */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        val GroupPolicy.staticNetwork: Map<String, Any>
            get() = if (containsKey(STATIC_NETWORK_TEMPLATE)) {
                get(STATIC_NETWORK_TEMPLATE) as? Map<String, Any>
                    ?: throw ClassCastException("Casting failed for static network from group policy JSON.")
            } else {
                emptyMap()
            }

        /** Static MGM. */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        val GroupPolicy.staticMgm: Map<String, String>
            get() = if (staticNetwork.containsKey(STATIC_MGM)) {
                staticNetwork[STATIC_MGM] as? Map<String, String>
                    ?: throw ClassCastException("Casting failed for static mgm from group policy JSON.")
            } else {
                emptyMap()
            }

        /** Static members. */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        val GroupPolicy.staticMembers: List<StaticMember>
            get() = if (staticNetwork.containsKey(STATIC_MEMBERS)) {
                (staticNetwork[STATIC_MEMBERS] as? List<Map<String, Any>>)?.map { StaticMember(it) }
                    ?: throw ClassCastException("Casting failed for static members from group policy JSON.")
            } else {
                emptyList()
            }

        /** Static MGM's private key alias. */
        @JvmStatic
        val GroupPolicy.mgmKeyAlias: String?
            get() = if (staticMgm.containsKey(MGM_KEY_ALIAS)) {
                staticMgm[MGM_KEY_ALIAS]
            } else {
                null
            }
    }
}