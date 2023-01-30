package net.corda.httprpc.server.config.impl

import net.corda.httprpc.server.config.RestServerSettingsProvider
import net.corda.httprpc.server.config.SsoSettingsProvider
import net.corda.httprpc.server.config.models.RestServerSettings
import org.slf4j.LoggerFactory
import java.nio.file.Path

class RestServerObjectSettingsProvider(
    private val restServerSettings: RestServerSettings,
    private val devMode: Boolean
) : RestServerSettingsProvider {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val ssoSettingsProvider: SsoSettingsProvider? by lazy {
        if (restServerSettings.sso != null) {
            SsoObjectSettingsProvider(restServerSettings.sso)
        } else {
            null
        }
    }

    init {
        log.info("REST config instantiated:\n${restServerSettings}, devMode=$devMode")
    }

    override fun getApiVersion() = restServerSettings.context.version

    override fun getHostAndPort() = restServerSettings.address

    override fun getBasePath() = restServerSettings.context.basePath

    override fun getApiTitle(): String = restServerSettings.context.title

    override fun getApiDescription(): String = restServerSettings.context.description

    override fun getSSLKeyStorePath(): Path? = restServerSettings.ssl?.keyStorePath

    override fun getSSLKeyStorePassword(): String? = restServerSettings.ssl?.keyStorePassword

    override fun isDevModeEnabled(): Boolean = devMode

    override fun getSsoSettings(): SsoSettingsProvider? = ssoSettingsProvider

    override fun maxContentLength(): Int = restServerSettings.maxContentLength

    override fun getWebSocketIdleTimeoutMs(): Long = restServerSettings.webSocketIdleTimeoutMs
}
