package net.corda.libs.statemanager.impl.configuration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_DRIVER
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_PASS
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_PERSISTENCE_UNIT_NAME
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_IDLE_TIMEOUT_SECONDS
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_KEEP_ALIVE_TIME_SECONDS
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_MAX_LIFETIME_SECONDS
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_MAX_SIZE
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_MIN_SIZE
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_POOL_VALIDATION_TIMEOUT_SECONDS
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_URL
import net.corda.schema.configuration.MessagingConfig.StateManager.JDBC_USER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StateManagerConfigProviderTest {

    @Test
    fun `get state manager config with the basic required properties`() {
        val config = ConfigFactory.empty()
            .withValue(JDBC_URL, fromAnyRef("url1"))
            .withValue(JDBC_USER, fromAnyRef("user1"))
            .withValue(JDBC_PASS, fromAnyRef("pass1"))

        val configWithFallback = StateManagerConfigProvider.getConfigWithFallback(config)

        assertThat(configWithFallback.getString(JDBC_URL)).isEqualTo("url1")
        assertThat(configWithFallback.getString(JDBC_USER)).isEqualTo("user1")
        assertThat(configWithFallback.getString(JDBC_PASS)).isEqualTo("pass1")
        assertThat(configWithFallback.getString(JDBC_DRIVER)).isEqualTo("org.postgresql.Driver")
        assertThat(configWithFallback.getString(JDBC_PERSISTENCE_UNIT_NAME)).isEqualTo("corda-state-manager")
        assertThat(configWithFallback.getInt(JDBC_POOL_MIN_SIZE)).isEqualTo(1)
        assertThat(configWithFallback.getInt(JDBC_POOL_MAX_SIZE)).isEqualTo(5)
        assertThat(configWithFallback.getInt(JDBC_POOL_IDLE_TIMEOUT_SECONDS)).isEqualTo(120)
        assertThat(configWithFallback.getInt(JDBC_POOL_MAX_LIFETIME_SECONDS)).isEqualTo(1800)
        assertThat(configWithFallback.getInt(JDBC_POOL_KEEP_ALIVE_TIME_SECONDS)).isEqualTo(0)
        assertThat(configWithFallback.getInt(JDBC_POOL_VALIDATION_TIMEOUT_SECONDS)).isEqualTo(5)
    }

    @Test
    fun `get state manager config with fallback gives priority to the config`() {
        val config = ConfigFactory.empty()
            .withValue(JDBC_URL, fromAnyRef("url1"))
            .withValue(JDBC_USER, fromAnyRef("user1"))
            .withValue(JDBC_PASS, fromAnyRef("pass1"))
            .withValue(JDBC_DRIVER, fromAnyRef("newdriver"))
            .withValue(JDBC_PERSISTENCE_UNIT_NAME, fromAnyRef("newunit"))
            .withValue(JDBC_POOL_MIN_SIZE, fromAnyRef(100))
            .withValue(JDBC_POOL_MAX_SIZE, fromAnyRef(100))
            .withValue(JDBC_POOL_IDLE_TIMEOUT_SECONDS, fromAnyRef(100))
            .withValue(JDBC_POOL_MAX_LIFETIME_SECONDS, fromAnyRef(100))
            .withValue(JDBC_POOL_KEEP_ALIVE_TIME_SECONDS, fromAnyRef(100))
            .withValue(JDBC_POOL_VALIDATION_TIMEOUT_SECONDS, fromAnyRef(100))

        val configWithFallback = StateManagerConfigProvider.getConfigWithFallback(config)

        assertThat(configWithFallback.getString(JDBC_URL)).isEqualTo("url1")
        assertThat(configWithFallback.getString(JDBC_USER)).isEqualTo("user1")
        assertThat(configWithFallback.getString(JDBC_PASS)).isEqualTo("pass1")
        assertThat(configWithFallback.getString(JDBC_DRIVER)).isEqualTo("newdriver")
        assertThat(configWithFallback.getString(JDBC_PERSISTENCE_UNIT_NAME)).isEqualTo("newunit")
        assertThat(configWithFallback.getInt(JDBC_POOL_MIN_SIZE)).isEqualTo(100)
        assertThat(configWithFallback.getInt(JDBC_POOL_MAX_SIZE)).isEqualTo(100)
        assertThat(configWithFallback.getInt(JDBC_POOL_IDLE_TIMEOUT_SECONDS)).isEqualTo(100)
        assertThat(configWithFallback.getInt(JDBC_POOL_MAX_LIFETIME_SECONDS)).isEqualTo(100)
        assertThat(configWithFallback.getInt(JDBC_POOL_KEEP_ALIVE_TIME_SECONDS)).isEqualTo(100)
        assertThat(configWithFallback.getInt(JDBC_POOL_VALIDATION_TIMEOUT_SECONDS)).isEqualTo(100)
    }
}