package net.corda.restclient.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * ChangeOtherUserPasswordWrapperRequest
 *
 * @param password The new password to apply.
 * @param username Username for the password change.
 */


data class ChangeOtherUserPasswordWrapperRequest (

    /* The new password to apply. */
    @field:JsonProperty("password")
    val password: kotlin.String,

    /* Username for the password change. */
    @field:JsonProperty("username")
    val username: kotlin.String

)