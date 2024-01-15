package net.corda.membership.lib

import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import org.slf4j.LoggerFactory

object VersionedMessageBuilder {
    private val logger = LoggerFactory.getLogger("net.corda.membership.lib.VersionedMessageBuilder.kt")

    @JvmStatic
    fun retrieveRegistrationStatusMessage(platformVersion: Int, registrationId: String, status: String, reason: String?) =
        try {
            if (platformVersion < 50100) {
                SetOwnRegistrationStatus(registrationId, RegistrationStatus.valueOf(status))
            } else {
                SetOwnRegistrationStatusV2(registrationId, RegistrationStatusV2.valueOf(status), reason)
            }
        } catch (e: IllegalArgumentException) {
            logger.warn("Could not retrieve status '$status', returning null.")
            null
        }
}

typealias SetOwnRegistrationStatusV2 = net.corda.data.membership.p2p.v2.SetOwnRegistrationStatus
typealias RegistrationStatusV2 = net.corda.data.membership.common.v2.RegistrationStatus
