package net.corda.membership.staticmemberlist

import net.corda.membership.GroupPolicy

class StaticMemberTemplateConstants {
    companion object {
        /** Key name for static member template property. */
        const val STATIC_MEMBER_TEMPLATE = "staticMemberTemplate"

        /** Key name for member X500Name. */
        const val X500NAME = "x500Name"

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

        /** Static member template. */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        val GroupPolicy.staticMemberTemplate: List<Map<String, String>>
            get() {
                return if (this.containsKey(STATIC_MEMBER_TEMPLATE)) this[STATIC_MEMBER_TEMPLATE] as List<Map<String, String>>
                else emptyList()
            }
    }
}
