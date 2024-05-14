package net.corda.restclient.dto

/**
 * ChangeOtherUserPasswordWrapperRequest
 *
 * @param password The new password to apply.
 * @param username Username for the password change.
 */


data class ChangeOtherUserPasswordWrapperRequest (

    /* The new password to apply. */
    val password: String,

    /* Username for the password change. */
    val username: String

)