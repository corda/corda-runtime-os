package net.corda.httprpc.server.config.impl

import net.corda.httprpc.server.config.HttpRpcSettingsProvider
import net.corda.httprpc.server.config.SsoSettingsProvider
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.v5.base.util.contextLogger
import java.nio.file.Path

class HttpRpcObjectSettingsProvider(
    private val httpRpcSettings: HttpRpcSettings,
    private val devMode: Boolean
) : HttpRpcSettingsProvider {
    private companion object {
        private val log = contextLogger()
    }

    private val ssoSettingsProvider: SsoSettingsProvider? by lazy {
        if (httpRpcSettings.sso != null) {
            SsoObjectSettingsProvider(httpRpcSettings.sso)
        } else {
            null
        }
    }

    init {
        log.info("Http Rpc config instantiated:\n${httpRpcSettings}, devMode=$devMode")
    }

    override fun isHttpRpcEnabled(): Boolean = true

    override fun getApiVersion() = httpRpcSettings.context.version

    override fun getHostAndPort() = httpRpcSettings.address

    override fun getBasePath() = httpRpcSettings.context.basePath

    override fun getApiTitle(): String = httpRpcSettings.context.title

    override fun getApiDescription(): String = httpRpcSettings.context.description

    override fun getSSLKeyStorePath(): Path? = httpRpcSettings.ssl?.keyStorePath

    override fun getSSLKeyStorePassword(): String? = httpRpcSettings.ssl?.keyStorePassword

    override fun isDevModeEnabled(): Boolean = devMode

    override fun getSsoSettings(): SsoSettingsProvider? = ssoSettingsProvider

    override fun maxContentLength(): Int = httpRpcSettings.maxContentLength

    override fun getWebSocketIdleTimeoutMs(): Long = httpRpcSettings.webSocketIdleTimeoutMs
}
