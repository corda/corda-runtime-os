package net.corda.membership.rest.v1.types.request

/**
 * Parameters for suspending or activating a member.
 *
 * @param x500Name X.500 name of the member being suspended or activated.
 * @param serialNumber Optional. Serial number of the member's [MemberInfo].
 * @param reason Optional. Reason for suspension/activation.
 */
data class SuspensionActivationParameters(
    val x500Name: String,
    val serialNumber: Int? = null,
    val reason: String? = null,
)
