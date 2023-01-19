package net.corda.membership.client.dto

import java.time.Instant

data class PreAuthTokenDto(
    val id: String,
    val ownerX500Name: String,
    val ttl: Instant,
    val status: PreAuthTokenStatusDTO,
    val remarks: String?
)

enum class PreAuthTokenStatusDTO {
    AVAILABLE,
    REVOKED,
    CONSUMED,
    AUTO_INVALIDATED
}