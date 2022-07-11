package net.corda.membership.impl.registration.staticnetwork

class StaticMemberTemplateExtension {
    companion object {
        /** Key name for member's name. */
        const val NAME = "name"

        /** Key name for key alias. */
        const val KEY_ALIAS = "keyAlias"

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
    }
}