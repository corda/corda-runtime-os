package net.corda.applications.workers.workercommon.internal

import com.typesafe.config.Config
import net.corda.applications.workers.workercommon.StateManagerConfigHelper.DEFAULT_DRIVER
import net.corda.applications.workers.workercommon.StateManagerConfigHelper.DEFAULT_JDBC_POOL_IDLE_TIMEOUT_SECONDS
import net.corda.applications.workers.workercommon.StateManagerConfigHelper.DEFAULT_JDBC_POOL_KEEP_ALIVE_TIME_SECONDS
import net.corda.applications.workers.workercommon.StateManagerConfigHelper.DEFAULT_JDBC_POOL_MAX_LIFETIME_SECONDS
import net.corda.applications.workers.workercommon.StateManagerConfigHelper.DEFAULT_JDBC_POOL_MAX_SIZE
import net.corda.applications.workers.workercommon.StateManagerConfigHelper.DEFAULT_JDBC_POOL_MIN_SIZE
import net.corda.applications.workers.workercommon.StateManagerConfigHelper.DEFAULT_JDBC_POOL_VALIDATION_TIMEOUT_SECONDS
import net.corda.schema.configuration.StateManagerConfig
import org.assertj.core.api.SoftAssertions

@Suppress("LongParameterList")
fun assertStateType(
    softly: SoftAssertions, config: Config, stateType: StateManagerConfig.StateType,
    user: String = "${stateType.value}-user", pass: String = "${stateType.value}-pass", url: String = "${stateType.value}-url",
    driver: String = DEFAULT_DRIVER,
    minSize: Int = DEFAULT_JDBC_POOL_MIN_SIZE, maxSize: Int = DEFAULT_JDBC_POOL_MAX_SIZE,
    idleTimeout: Int = DEFAULT_JDBC_POOL_IDLE_TIMEOUT_SECONDS, maxLifetime: Int = DEFAULT_JDBC_POOL_MAX_LIFETIME_SECONDS,
    keepAlive: Int = DEFAULT_JDBC_POOL_KEEP_ALIVE_TIME_SECONDS, validationTimeout: Int = DEFAULT_JDBC_POOL_VALIDATION_TIMEOUT_SECONDS,
) {
    softly.assertThat(config.hasPath(stateType.value))
    val typeConfig = config.getConfig(stateType.value)
    softly.assertThat(typeConfig.getString(StateManagerConfig.TYPE)).isEqualTo("Database")
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
