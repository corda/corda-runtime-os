package net.corda.libs.permissions.manager

import net.corda.data.rest.PasswordExpiryStatus

data class AuthenticationState(val authenticationSuccess: Boolean, val expiryStatus: PasswordExpiryStatus)
