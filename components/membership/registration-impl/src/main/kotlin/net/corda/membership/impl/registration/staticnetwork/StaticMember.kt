package net.corda.membership.impl.registration.staticnetwork

import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.MEMBER_STATUS
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_MODIFIED_TIME
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.utilities.time.UTCClock
import net.corda.v5.membership.EndpointInfo

/**
 * Class which represents a static member. Static members are Map<String, Any> which we need
 * to map to Map<String, String>. This class provides access to properties always as strings.
 */
class StaticMember(
    private val staticMemberData: Map<String, Any>,
    private val endpointInfoBuilder: (String, Int) -> EndpointInfo,
) : Map<String, Any> by staticMemberData {

    private companion object {
        const val DEFAULT_SERIAL = "1"
        private val clock = UTCClock()
    }

    val name: String?
        get() = getStringValue(StaticMemberTemplateExtension.NAME)

    val serial: String
        get() = getIntValueAsString(STATIC_SERIAL, DEFAULT_SERIAL)!!

    val status: String
        get() = getStringValue(MEMBER_STATUS, MEMBER_STATUS_ACTIVE)!!

    val modifiedTime: String
        get() = getStringValue(STATIC_MODIFIED_TIME, clock.instant().toString())!!

    private fun getStringValue(key: String, default: String? = null): String? =
        staticMemberData[key] as String? ?: default

    private fun getIntValueAsString(key: String, default: String? = null): String? =
        (staticMemberData[key] as Int?)?.toString() ?: default

    fun getEndpoint(index: Int): EndpointInfo {
        return endpointInfoBuilder(
            staticMemberData[String.format(ENDPOINT_URL, index)].toString(),
            Integer.parseInt(staticMemberData[String.format(ENDPOINT_PROTOCOL, index)].toString())
        )
    }
}
