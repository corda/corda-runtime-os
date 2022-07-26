package net.corda.membership.lib.impl.grouppolicy

import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID

private const val MISSING_KEY_ERROR = "Group policy is missing configuration. Missing key: %s"
private const val MISSING_CERT_ERROR = "Policy file must contain at least one trust root for %s."
private const val BAD_CERT_ERROR = "Certificate at index %s for %s cannot be parsed."
private const val BAD_TYPE_ERROR = "Group policy has incorrect type. Key for wrong type: %s. Expected type: %s."
private const val BAD_ENUM_ERROR = "%s is not an allowed value for %s."
private const val BLANK_VALUE_ERROR = "Key \"%s\" cannot be blank."
const val BAD_MGM_GROUP_ID_ERROR = "Group ID in the group policy for an MGM must be \"$MGM_DEFAULT_GROUP_ID\""

fun getBlankValueError(key: String) = String.format(BLANK_VALUE_ERROR, key)
fun getBadTypeError(key: String, expected: String) = String.format(BAD_TYPE_ERROR, key, expected)
fun getBadEnumError(key: String, value: String) = String.format(BAD_ENUM_ERROR, value, key)
fun getBadCertError(key: String, index: Int) = String.format(BAD_CERT_ERROR, index, key)
fun getMissingKeyError(key: String) = String.format(MISSING_KEY_ERROR, key)
fun getMissingCertError(key: String) = String.format(MISSING_CERT_ERROR, key)
