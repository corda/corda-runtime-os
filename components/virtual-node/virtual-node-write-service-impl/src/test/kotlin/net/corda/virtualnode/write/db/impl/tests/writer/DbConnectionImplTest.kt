package net.corda.virtualnode.write.db.impl.tests.writer

import com.typesafe.config.ConfigFactory
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.virtualnode.write.db.impl.writer.DbConnectionImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DbConnectionImplTest {

    @Test
    fun `set user`() {
        val config = getConfig(mapOf("database.user" to "user1"))

        val dbConnection = DbConnectionImpl(
            "name",
            DbPrivilege.DDL,
            config,
            "description"
        )

        assertThat(dbConnection.getUser()).isEqualTo("user1")
    }

    @Test
    fun `set user to null on missing config`() {
        val config = getConfig(mapOf())

        val dbConnection = DbConnectionImpl(
            "name",
            DbPrivilege.DDL,
            config,
            "description"
        )

        assertThat(dbConnection.getUser()).isNull()
    }

    @Test
    fun `set password`() {
        val config = getConfig(mapOf("database.pass" to "password1"))

        val dbConnection = DbConnectionImpl(
            "name",
            DbPrivilege.DDL,
            config,
            "description"
        )

        assertThat(dbConnection.getPassword()).isEqualTo("password1")
    }

    @Test
    fun `set password to null on missing config`() {
        val config = getConfig(mapOf())

        val dbConnection = DbConnectionImpl(
            "name",
            DbPrivilege.DDL,
            config,
            "description"
        )

        assertThat(dbConnection.getPassword()).isNull()
    }


    private fun getConfig(config: Map<String, Any>): SmartConfig {
        return SmartConfigFactory.createWithoutSecurityServices().create(
            ConfigFactory.parseMap(config)
        )
    }
}