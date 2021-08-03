package net.corda.p2p.gateway.messaging

import java.security.KeyStore

/**
 * Simple configuration to be used for one-way TLS between Gateways. Since a gateway is both a client and a server,
 * it will require all properties to be configured. The [keyStore] and [keyStorePassword] respectively are needed by the
 * server to present a valid certificate during TLS handshake, whereas the [trustStore] and [trustStorePassword] are used
 * by the client to validate the received certificate.
 */
interface SslConfiguration {
    /**
     * The key store used for TLS connections
     */
    val keyStore: KeyStore

    /**
     * The password for the key store
     */
    val keyStorePassword: String

    /**
     * The trust root key store used to validate the peer certificate
     */
    val trustStore: KeyStore

    /**
     * The trust store password
     */
    val trustStorePassword: String

    /**
     * Property determining how the revocation check will be made for the server certificate
     */
    val revocationCheck: RevocationConfig
}