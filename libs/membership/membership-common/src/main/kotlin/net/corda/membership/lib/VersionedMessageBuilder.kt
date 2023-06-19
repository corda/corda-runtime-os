package net.corda.membership.lib

import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import org.apache.avro.specific.SpecificRecordBase

private val regexForV50000 = "500[0-9][0-9]$".toRegex()
private val regexForV50100 = "501[0-9][0-9]$".toRegex()

fun retrieveRegistrationStatusMessage(platformVersion: Int, registrationId: String, status: String): SpecificRecordBase {
    val platformVersionString = platformVersion.toString()
    return when {
        regexForV50000.matches(platformVersionString) -> SetOwnRegistrationStatus(registrationId, retrieveV1Status(status))
        regexForV50100.matches(platformVersionString) -> SetOwnRegistrationStatusV2(registrationId, retrieveV2Status(status))
        else -> throw IllegalArgumentException("Unknown platform version: $platformVersion")
    }
}

private fun retrieveV1Status(status: String): RegistrationStatus {
    return when (status) {
        StatusesBeingSent.RECEIVED_BY_MGM.name -> RegistrationStatus.RECEIVED_BY_MGM
        StatusesBeingSent.PENDING_MANUAL_APPROVAL.name -> RegistrationStatus.PENDING_MANUAL_APPROVAL
        StatusesBeingSent.PENDING_AUTO_APPROVAL.name -> RegistrationStatus.PENDING_AUTO_APPROVAL
        StatusesBeingSent.APPROVED.name -> RegistrationStatus.APPROVED
        StatusesBeingSent.DECLINED.name -> RegistrationStatus.DECLINED
        else -> throw IllegalArgumentException(exceptionMessageForInvalidStatus(status))
    }
}

private fun retrieveV2Status(status: String): RegistrationStatusV2 {
    return when (status) {
        StatusesBeingSent.RECEIVED_BY_MGM.name -> RegistrationStatusV2.RECEIVED_BY_MGM
        StatusesBeingSent.PENDING_MANUAL_APPROVAL.name -> RegistrationStatusV2.PENDING_MANUAL_APPROVAL
        StatusesBeingSent.PENDING_AUTO_APPROVAL.name -> RegistrationStatusV2.PENDING_AUTO_APPROVAL
        StatusesBeingSent.APPROVED.name -> RegistrationStatusV2.APPROVED
        StatusesBeingSent.DECLINED.name -> RegistrationStatusV2.DECLINED
        else -> throw IllegalArgumentException(exceptionMessageForInvalidStatus(status))
    }
}

private fun exceptionMessageForInvalidStatus(status: String) = "This status '$status' should not be distributed."

typealias SetOwnRegistrationStatusV2 = net.corda.data.membership.p2p.v2.SetOwnRegistrationStatus
typealias RegistrationStatusV2 = net.corda.data.membership.common.v2.RegistrationStatus

enum class StatusesBeingSent {
    RECEIVED_BY_MGM,
    PENDING_MANUAL_APPROVAL,
    PENDING_AUTO_APPROVAL,
    APPROVED,
    DECLINED
}