package net.corda.libs.permissions.manager

data class AuthenticationState(val authenticationSuccess: Boolean, val expiryStatus: ExpiryStatus?)

enum class ExpiryStatus {
    ACTIVE,
    CLOSE_TO_EXPIRY,
    EXPIRED
}
