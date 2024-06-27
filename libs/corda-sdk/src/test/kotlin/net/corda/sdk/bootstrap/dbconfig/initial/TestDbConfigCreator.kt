package net.corda.sdk.bootstrap.dbconfig.initial

import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.sdk.bootstrap.initial.createConfigDbConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TestDbConfigCreator {

    @Test
    fun `test DbConfig creation directly with escaped string`() {
        val jdbcUrl = ""
        val jdbcPoolMaxSize = 10
        val jdbcPoolMinSize: Int? = null
        val idleTimeout: Int = 120
        val maxLifetime: Int = 1800
        val keepaliveTime: Int = 0
        val validationTimeout: Int = 5
        val username = "test\"user"
        val password = ""
        val salt = "123"
        val passphrase = "123"
        val vaultKey = "corda-config-database-password"
        val secretsService: SecretsCreateService = EncryptionSecretsServiceImpl(passphrase, salt)

        val outText = createConfigDbConfig(
            jdbcUrl, username, password, vaultKey, jdbcPoolMaxSize, jdbcPoolMinSize,
            idleTimeout, maxLifetime, keepaliveTime, validationTimeout, secretsService
        )

        assertThat(outText).contains("\"user\":\"test\\\"user\"")
    }
}
