package net.corda.orm.impl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PostgresEntityManagerConfigurationTest {
    @Test
    fun `set default config values`() {
        val dataSourceFactory = mock<(HikariConfig) -> HikariDataSource>() {
            on { invoke(any()) } doReturn(mock())
        }

        val conf: EntityManagerConfiguration = PostgresEntityManagerConfiguration(
            "jdbcUrl",
            "user",
            "pass",
            hikariDataSourceFactory = dataSourceFactory
        )

        assertThat(conf.showSql).isEqualTo(false)
        assertThat(conf.formatSql).isEqualTo(false)
        assertThat(conf.ddlManage).isEqualTo(DdlManage.NONE)

        conf.dataSource
        verify(dataSourceFactory).invoke(
            argThat { it ->
                it.jdbcUrl == "jdbcUrl" &&
                    it.username == "user" &&
                    it.password == "pass" &&
                    it.driverClassName == "org.postgresql.Driver" &&
                    !it.isAutoCommit &&
                    it.maximumPoolSize == 10
            }
        )
    }
}
