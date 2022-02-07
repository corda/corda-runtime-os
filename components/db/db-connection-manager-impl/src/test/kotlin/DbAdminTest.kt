import com.typesafe.config.ConfigFactory
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.impl.DbAdminImpl
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.sql.Connection
import java.sql.Statement
import javax.sql.DataSource

class DbAdminTest {
    private val statement = mock<Statement>()
    private val connection = mock<Connection>() {
        on { createStatement() }.doReturn(statement)
    }
    private val dataSource = mock<DataSource>() {
        on { connection }.doReturn(connection)
    }
    private val dbConnectionsRepository = mock<DbConnectionsRepository>() {
        on { clusterDataSource }.doReturn(dataSource)
    }
    private val secretConfig = SmartConfigImpl(ConfigFactory.empty(), mock(), mock())
    private val config = mock<SmartConfig>()
    private val configFactory = mock<SmartConfigFactory>() {
        on { create(any()) }.doReturn(config)
        on { makeSecret(any()) }.doReturn(secretConfig)
    }

    @Test
    fun `when create DDL user grant all`() {
        val dba = DbAdminImpl(dbConnectionsRepository)

        dba.createDbAndUser(
            "test",
            "test-schema",
            "test-user",
            "test-password",
            "test-url",
            DbPrivilege.DDL,
            configFactory
        )

        verify(statement).execute(argThat { this.contains("GRANT ALL ON SCHEMA") })
    }

    @Test
    fun `when create DML limited grant`() {
        val dba = DbAdminImpl(dbConnectionsRepository)

        dba.createDbAndUser(
            "test",
            "test-schema",
            "test-user",
            "test-password",
            "test-url",
            DbPrivilege.DML,
            configFactory
        )

        verify(statement).execute(argThat { this.contains("GRANT SELECT, UPDATE, INSERT, DELETE ON ALL TABLES IN SCHEMA") })
    }
}