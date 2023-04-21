package net.corda.rest.server.config

import net.corda.utilities.NetworkHostAndPort
import java.nio.file.Path

/**
 * Interface that provides a way to retrieve REST server config values, agnostic of particular config providing implementation.
 */
interface RestServerSettingsProvider {

    /**
     * @return the API version
     */
    fun getApiVersion(): String

    /**
     * @return the host and port of the address
     */
    fun getHostAndPort(): NetworkHostAndPort

    /**
     * @return the base path for the api routes
     */
    fun getBasePath(): String

    /**
     * @return the API title
     */
    fun getApiTitle(): String

    /**
     * @return the API description
     */
    fun getApiDescription(): String

    /**
     * @return The SSL key store path
     */
    fun getSSLKeyStorePath(): Path?

    /**
     * @return The SSL key store password
     */
    fun getSSLKeyStorePassword(): String?

    /**
     * @return whether the node is operating in dev mode
     */
    fun isDevModeEnabled(): Boolean

    /**
     * @return SSO settings
     */
    fun getSsoSettings(): SsoSettingsProvider?

    fun maxContentLength(): Int

    /**
     * @return The time (in milliseconds) after which an idle websocket connection will be timed out and closed
     */
    fun getWebSocketIdleTimeoutMs(): Long
}
