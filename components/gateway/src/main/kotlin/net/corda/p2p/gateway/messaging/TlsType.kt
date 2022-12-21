package net.corda.p2p.gateway.messaging

enum class TlsType {
    /**
     * Only validate the TLS certificates for the gateway server.
     */
    ONE_WAY,

    /**
     * Validate the TLS certificates for both the gateway server and client.
     */
    MUTUAL,
}