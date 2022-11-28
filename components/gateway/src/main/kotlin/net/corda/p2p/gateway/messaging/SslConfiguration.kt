package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config

/**
 * Simple configuration to be used for one-way TLS between Gateways. Since a gateway is both a client and a server,
 * it will require all properties to be configured.
 */
data class SslConfiguration(
    /**
     * Property determining how the revocation check will be made for the server certificate
     */
    val revocationCheck: RevocationConfig,

    val tlsType: TlsType,
)

enum class TlsType {
    MUTUAL,
    ONE_WAY,
}

internal fun Config.toSslConfiguration(): SslConfiguration {
    val revocationCheckMode = this.getEnum(RevocationConfigMode::class.java, "revocationCheck.mode")
    val tlsType = this.getEnum(TlsType::class.java, "tlsType")
    return SslConfiguration(
        revocationCheck = RevocationConfig(revocationCheckMode),
        tlsType = tlsType,
    )
}
