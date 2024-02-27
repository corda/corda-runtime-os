@file:JvmName("RegistrationRequestConstants")

package net.corda.membership.lib.registration

const val REGISTRATION_PUBLIC_KEY = "corda.registration.request.public.key"
const val REGISTRATION_SIGNATURE = "corda.registration.request.signature"
const val REGISTRATION_CONTEXT = "corda.registration.request.context"

/** Key name for pre-auth token property. */
const val PRE_AUTH_TOKEN = "corda.auth.token"

/**
 * Reasons for declination of a registration request.
 */
const val DECLINED_REASON_FOR_USER_INTERNAL_ERROR = "Internal error on the MGM side. " +
    "Please reach out to the network operator to find out the reason the request was declined."

// The below should be used in scenarios where we don't want to leak sensitive information.
const val DECLINED_REASON_FOR_USER_GENERAL_INVALID_REASON = "Invalid request. " +
    "Please reach out to the network operator to find out the reason the request was declined."
const val DECLINED_REASON_FOR_USER_GENERAL_MANUAL_DECLINED = "The request was manually declined by the network operator. " +
    "Please reach out to them to find out the reason the request was declined."
const val DECLINED_REASON_EMPTY_REGISTRATION_CONTEXT = "Empty member context in the registration request."
const val DECLINED_REASON_NOT_MGM_IDENTITY = "Registration request is targeted at non-MGM holding identity."
const val DECLINED_REASON_NAME_IN_REQUEST_NOT_MATCHING_NAME_IN_P2P_MSG =
    "MemberX500Name in registration request does not match member sending request over P2P."
const val DECLINED_REASON_SERIAL_NULL = "Serial on the registration request should not be null."
const val DECLINED_REASON_SERIAL_NEGATIVE = "Serial cannot be negative on the registration request."
const val DECLINED_REASON_RESISTRANT_IS_MGM = "Registration request is registering an MGM holding identity."
const val DECLINED_REASON_GROUP_ID_IN_REQUEST_NOT_MATCHING_TARGET =
    "Group ID in registration request does not match the group ID of the target MGM."
const val DECLINED_REASON_NO_ENDPOINTS_SPECIFIED = "Registering member has not specified any endpoints"
const val DECLINED_REASON_NOTARY_MISSING_NOTARY_DETAILS = "Registering member has role set to 'notary', but has missing notary key details."
const val DECLINED_REASON_INVALID_NOTARY_SERVICE_PLUGIN_TYPE = "Registering member has specified an invalid notary service plugin type."
const val DECLINED_REASON_COMMS_ISSUE = "MGM could not establish communication back to registering member."
const val DECLINED_REASON_NOTARY_LEDGER_KEY = "A notary virtual node cannot be registered with a ledger key."
