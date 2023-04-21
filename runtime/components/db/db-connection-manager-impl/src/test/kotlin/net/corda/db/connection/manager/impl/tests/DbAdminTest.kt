package net.corda.db.connection.manager.impl.tests

import net.corda.db.connection.manager.DbAdmin
import net.corda.db.core.DbPrivilege
import org.junit.jupiter.api.Test
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
    val dba = object : DbAdmin() {
        override fun bindDataSource() = mock<DataSource> {
            on { connection }.doReturn(connection)
        }
    }

    @Test
    fun `when create DDL user grant all`() {
        dba.createDbAndUser(
            "test-schema",
            "test-user",
            "test-password",
            DbPrivilege.DDL
        )

        verify(statement).execute(argThat {
            this.contains("GRANT ALL ON SCHEMA test-schema")
            this.contains("CREATE SCHEMA IF NOT EXISTS")
                    && this.contains("CREATE USER test-user WITH PASSWORD 'test-password'")
                    && this.contains("GRANT USAGE ON SCHEMA test-schema to test-user;")
        })
    }

    @Test
    fun `when create DB and DDL user grant all`() {
        dba.createDbAndUser(
            "test-schema",
            "test-user",
            "test-password",
            DbPrivilege.DDL
        )

        verify(statement).execute(argThat {
            this.contains("CREATE SCHEMA IF NOT EXISTS")
                    && this.contains("CREATE USER test-user WITH PASSWORD 'test-password'")
                    && this.contains("GRANT USAGE ON SCHEMA test-schema to test-user;")
                    && this.contains("GRANT ALL ON SCHEMA")
        })
    }

    @Test
    fun `when create DML limited grant`() {
        dba.createDbAndUser(
            "test-schema",
            "test-user",
            "test-password",
            DbPrivilege.DML
        )

        verify(statement).execute(argThat {
            this.contains("ALTER DEFAULT PRIVILEGES IN SCHEMA test-schema GRANT SELECT, UPDATE, INSERT, DELETE ON TABLES")
                    && this.contains("CREATE SCHEMA IF NOT EXISTS")
                    && this.contains("CREATE USER test-user WITH PASSWORD 'test-password'")
                    && this.contains("GRANT USAGE ON SCHEMA test-schema to test-user;")
        })
    }

    @Test
    fun `when create DML without grantee provided then limited grant`() {
        dba.createDbAndUser(
            "test-schema",
            "test-user",
            "test-password",
            DbPrivilege.DML
        )

        verify(statement).execute(argThat {
            this.contains("CREATE SCHEMA IF NOT EXISTS")
                    && this.contains("CREATE USER test-user WITH PASSWORD 'test-password'")
                    && this.contains("GRANT USAGE ON SCHEMA test-schema to test-user;")
                    && this.contains("ALTER DEFAULT PRIVILEGES IN SCHEMA test-schema GRANT SELECT, UPDATE, INSERT, DELETE ON TABLES")
        })
    }

    @Test
    fun `when create DML with grantee provided then limited grant`() {
        dba.createDbAndUser(
            "test-schema",
            "test-user",
            "test-password",
            DbPrivilege.DML,
            "test-grantee"
        )

        verify(statement).execute(argThat {
            this.contains("CREATE SCHEMA IF NOT EXISTS")
                    && this.contains("CREATE USER test-user WITH PASSWORD 'test-password'")
                    && this.contains("GRANT USAGE ON SCHEMA test-schema to test-user;")
                    && this.contains(
                "ALTER DEFAULT PRIVILEGES FOR ROLE test-grantee IN SCHEMA test-schema " +
                        "GRANT SELECT, UPDATE, INSERT, DELETE ON TABLES"
            )
        })
    }
}
