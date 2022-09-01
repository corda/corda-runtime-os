package net.corda.httprpc.server.config

import net.corda.base.util.NetworkHostAndPort
import java.nio.file.Path

/**
 * Interface that provides a way to retrieve HTTP RPC config values, agnostic of particular config providing implementation.
 */
interface HttpRpcSettingsProvider {
    /**
     * @return whether the HTTP RPC functionality is enabled and the server is accessible.
     */
    fun isHttpRpcEnabled(): Boolean

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
}
