package net.corda.httprpc.server.impl.config.models

import net.corda.httprpc.server.config.models.AzureAdSettings
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.config.models.HttpRpcSSLSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.config.models.SsoSettings
import net.corda.v5.base.util.NetworkHostAndPort
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Path

class HttpRpcSettingsTest {

    private companion object {
        const val clientId = "client"
        const val tenantId = "tenant"
        const val issuer = "mock://login.microsoftonline.com/$tenantId/v2.0"
    }

    @Test
    fun `Sensitive data should be omitted`() {
        val httpRpcSettingsStr = HttpRpcSettings(
            NetworkHostAndPort("localhost", 8080),
            HttpRpcContext("1", "api", "HttpRpcContext test title ", "HttpRpcContext test description"),
            HttpRpcSSLSettings(Path.of("path"), "pwd"),
            SsoSettings(
                AzureAdSettings(
                    clientId,
                    "secretValue",
                    tenantId,
                    trustedIssuers = listOf(issuer)
                )
            ), HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE
        )
            .toString()

        assertFalse(httpRpcSettingsStr.contains("keyStorePassword", true))
        assertFalse(httpRpcSettingsStr.contains("pwd", true))
        assertFalse(httpRpcSettingsStr.contains("clientSecret", true))
        assertFalse(httpRpcSettingsStr.contains("secretValue", true))
    }
}