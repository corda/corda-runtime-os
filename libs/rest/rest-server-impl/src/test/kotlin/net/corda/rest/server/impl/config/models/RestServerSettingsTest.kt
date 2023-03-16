package net.corda.rest.server.impl.config.models

import net.corda.rest.server.config.models.AzureAdSettings
import net.corda.rest.server.config.models.RestContext
import net.corda.rest.server.config.models.RestSSLSettings
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.server.config.models.SsoSettings
import net.corda.utilities.NetworkHostAndPort
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Path

class RestServerSettingsTest {

    private companion object {
        const val clientId = "client"
        const val tenantId = "tenant"
        const val issuer = "mock://login.microsoftonline.com/$tenantId/v2.0"
    }

    @Test
    fun `Sensitive data should be omitted`() {
        val restServerSettingsStr = RestServerSettings(
            NetworkHostAndPort("localhost", 8080),
            RestContext("1", "api", "RestContext test title ", "RestContext test description"),
            RestSSLSettings(Path.of("path"), "pwd"),
            SsoSettings(
                AzureAdSettings(
                    clientId,
                    "secretValue",
                    tenantId,
                    trustedIssuers = listOf(issuer)
                )
            ),
            RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
            20000L
        )
            .toString()

        assertFalse(restServerSettingsStr.contains("keyStorePassword", true))
        assertFalse(restServerSettingsStr.contains("pwd", true))
        assertFalse(restServerSettingsStr.contains("clientSecret", true))
        assertFalse(restServerSettingsStr.contains("secretValue", true))
    }
}