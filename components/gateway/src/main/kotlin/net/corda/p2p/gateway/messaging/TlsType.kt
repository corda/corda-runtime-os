package net.corda.p2p.gateway.messaging

enum class TlsType {
    /**
     * Establishes a regular TLS connection, where the server gateway will be authenticated.
     */
    ONE_WAY,

    /**
     * Establishes a mutual TLS connection, where both the server and client gateway are authenticated.
     */
    MUTUAL,
}