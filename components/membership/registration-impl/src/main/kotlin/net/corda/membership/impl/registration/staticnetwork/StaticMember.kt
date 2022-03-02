package net.corda.membership.impl.registration.staticnetwork

import net.corda.membership.identity.EndpointInfoImpl
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.MEMBER_STATUS
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_MODIFIED_TIME
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_PLATFORM_VERSION
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_SERIAL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_SOFTWARE_VERSION
import net.corda.v5.membership.identity.EndpointInfo
import java.time.Instant

/**
 * Class which represents a static member. Static members are Map<String, Any> which we need
 * to map to Map<String, String>. This class provides access to properties always as strings.
 */
class StaticMember(private val staticMemberData: Map<String, Any>) : Map<String, Any> by staticMemberData {

    private companion object {
        const val DEFAULT_SOFTWARE_VERSION = "5.0.0"
        const val DEFAULT_PLATFORM_VERSION = "10"
        const val DEFAULT_SERIAL = "1"
    }

    val name: String?
        get() = getStringValue(StaticMemberTemplateExtension.NAME)

    val keyAlias: String?
        get() = getStringValue(StaticMemberTemplateExtension.KEY_ALIAS)

    val softwareVersion: String
        get() = getStringValue(STATIC_SOFTWARE_VERSION, DEFAULT_SOFTWARE_VERSION)!!

    val platformVersion: String
        get() = getIntValueAsString(STATIC_PLATFORM_VERSION, DEFAULT_PLATFORM_VERSION)!!

    val serial: String
        get() = getIntValueAsString(STATIC_SERIAL, DEFAULT_SERIAL)!!

    val status: String
        get() = getStringValue(MEMBER_STATUS, MEMBER_STATUS_ACTIVE)!!

    val modifiedTime: String
        get() = getStringValue(STATIC_MODIFIED_TIME, Instant.now().toString())!!

    private fun getStringValue(key: String, default: String? = null): String? =
        staticMemberData[key] as String? ?: default

    private fun getIntValueAsString(key: String, default: String? = null): String? =
        (staticMemberData[key] as Int?)?.toString() ?: default

    fun getEndpoint(index: Int): EndpointInfo {
        return EndpointInfoImpl(
            staticMemberData[String.format(ENDPOINT_URL, index)].toString(),
            staticMemberData[String.format(ENDPOINT_PROTOCOL, index)]!! as Int
        )
    }
}