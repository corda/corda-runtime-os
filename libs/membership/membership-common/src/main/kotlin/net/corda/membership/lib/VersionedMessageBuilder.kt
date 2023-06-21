package net.corda.membership.lib

import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus

fun retrieveRegistrationStatusMessage(platformVersion: Int, registrationId: String, status: String) =
    if (platformVersion < 50100) {
        SetOwnRegistrationStatus(registrationId, RegistrationStatus.valueOf(status))
    } else {
        SetOwnRegistrationStatusV2(registrationId, RegistrationStatusV2.valueOf(status))
    }

typealias SetOwnRegistrationStatusV2 = net.corda.data.membership.p2p.v2.SetOwnRegistrationStatus
typealias RegistrationStatusV2 = net.corda.data.membership.common.v2.RegistrationStatus