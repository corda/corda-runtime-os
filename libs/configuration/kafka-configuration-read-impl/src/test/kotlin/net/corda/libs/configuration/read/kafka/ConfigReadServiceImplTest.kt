package net.corda.libs.configuration.read.kafka

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigReadServiceImplTest {

    private lateinit var configReadService: ConfigReadServiceImpl
    private lateinit var configRepository: ConfigRepositoryImpl

    @BeforeEach
    fun beforeEach() {
        configRepository = ConfigRepositoryImpl()
        configRepository.storeConfiguration(ConfigUtil.testConfigMap())
        configReadService = ConfigReadServiceImpl(configRepository)
    }

    @Test
    fun testGetConfiguration() {
        val config = configReadService.getConfiguration("corda.database")

        Assertions.assertThat(config.getString("transactionIsolationLevel")).isEqualTo("READ_COMMITTED")
        Assertions.assertThat(config.getString("schema")).isEqualTo("corda")
        Assertions.assertThat(config.getBoolean("runMigration")).isEqualTo(true)
        Assertions.assertThat(config.getDouble("componentVersion")).isEqualTo(5.4)
    }

//    @Test
//    fun testParseConfiguration() {
//        val security = configReadService.parseConfiguration("corda.security", SecurityConfiguration::class.java)
//
//        Assertions.assertThat(security.authService.dataSource.type).isEqualTo(AuthDataSourceType.INMEMORY)
//        Assertions.assertThat(security.authService.dataSource.passwordEncryption).isEqualTo(PasswordEncryption.NONE)
//        Assertions.assertThat(security.authService.dataSource.users?.size).isEqualTo(2)
//        Assertions.assertThat(security.authService.dataSource.users!![0].username).isEqualTo("corda")
//        Assertions.assertThat(security.authService.id).isEqualTo(AuthServiceId("NODE_CONFIG"))
//
//    }
}
