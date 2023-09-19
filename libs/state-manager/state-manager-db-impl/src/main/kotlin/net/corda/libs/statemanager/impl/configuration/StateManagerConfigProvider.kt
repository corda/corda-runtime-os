package net.corda.libs.statemanager.impl.configuration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.corda.schema.configuration.MessagingConfig
import java.time.Duration

object StateManagerConfigProvider {
    fun getConfigWithFallback(stateManagerConfig: Config): Config {
        val defaultPoolConfig = ConfigFactory.empty()
            .withValue(MessagingConfig.StateManager.JDBC_DRIVER, fromAnyRef("org.postgresql.Driver"))
            .withValue(MessagingConfig.StateManager.JDBC_PERSISTENCE_UNIT_NAME, fromAnyRef("corda-state-manager"))
            .withValue(MessagingConfig.StateManager.JDBC_POOL_MIN_SIZE, fromAnyRef(1))
            .withValue(MessagingConfig.StateManager.JDBC_POOL_MAX_SIZE, fromAnyRef(5))
            .withValue(MessagingConfig.StateManager.JDBC_POOL_IDLE_TIMEOUT_SECONDS, fromAnyRef(Duration.ofMinutes(2).toSeconds()))
            .withValue(MessagingConfig.StateManager.JDBC_POOL_MAX_LIFETIME_SECONDS, fromAnyRef(Duration.ofMinutes(30).toSeconds()))
            .withValue(MessagingConfig.StateManager.JDBC_POOL_KEEP_ALIVE_TIME_SECONDS, fromAnyRef(Duration.ZERO.toSeconds()))
            .withValue(
                MessagingConfig.StateManager.JDBC_POOL_VALIDATION_TIMEOUT_SECONDS, fromAnyRef(Duration.ofSeconds(5).toSeconds())
            )
        return stateManagerConfig.withFallback(defaultPoolConfig)
    }
}