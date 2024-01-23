package net.corda.applications.workers.workercommon.internal

import com.typesafe.config.Config
import net.corda.schema.configuration.StateManagerConfig
import org.assertj.core.api.SoftAssertions

@Suppress("LongParameterList")
fun assertStateType(
    softly: SoftAssertions, config: Config, stateType: String,
    user: String = "$stateType-user", pass: String = "$stateType-pass", url: String = "$stateType-url",
    driver: String = "$stateType-driver",
    minSize: Int = 0, maxSize: Int = 5, idleTimeout: Int = 120, maxLifetime: Int = 1800, keepAlive: Int = 0, validationTimeout: Int = 5,
) {
    softly.assertThat(config.hasPath(stateType))
    val typeConfig = config.getConfig(stateType)
    softly.assertThat(typeConfig.getString(StateManagerConfig.TYPE)).isEqualTo("DATABASE")
    softly.assertThat(typeConfig.getString(StateManagerConfig.Database.JDBC_URL)).isEqualTo(url)
    softly.assertThat(typeConfig.getString(StateManagerConfig.Database.JDBC_USER)).isEqualTo(user)
    softly.assertThat(typeConfig.getString(StateManagerConfig.Database.JDBC_PASS)).isEqualTo(pass)
    softly.assertThat(typeConfig.getString(StateManagerConfig.Database.JDBC_DRIVER)).isEqualTo(driver)
    softly.assertThat(typeConfig.getInt(StateManagerConfig.Database.JDBC_POOL_MIN_SIZE)).isEqualTo(minSize)
    softly.assertThat(typeConfig.getInt(StateManagerConfig.Database.JDBC_POOL_MAX_SIZE)).isEqualTo(maxSize)
    softly.assertThat(typeConfig.getInt(StateManagerConfig.Database.JDBC_POOL_IDLE_TIMEOUT_SECONDS)).isEqualTo(idleTimeout)
    softly.assertThat(typeConfig.getInt(StateManagerConfig.Database.JDBC_POOL_MAX_LIFETIME_SECONDS)).isEqualTo(maxLifetime)
    softly.assertThat(typeConfig.getInt(StateManagerConfig.Database.JDBC_POOL_KEEP_ALIVE_TIME_SECONDS)).isEqualTo(keepAlive)
    softly.assertThat(typeConfig.getInt(StateManagerConfig.Database.JDBC_POOL_VALIDATION_TIMEOUT_SECONDS)).isEqualTo(validationTimeout)
}
