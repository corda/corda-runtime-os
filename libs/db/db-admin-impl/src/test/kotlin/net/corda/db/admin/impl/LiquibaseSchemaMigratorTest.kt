package net.corda.db.admin.impl

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseConnection
import liquibase.resource.ResourceAccessor
import net.corda.db.admin.DbChange
import net.corda.db.admin.LiquibaseSchemaMigrator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.Writer
import java.sql.Connection

class LiquibaseSchemaMigratorTest {
    private val connection = mock<Connection>()
    private val dbChange = mock<DbChange>()
    private val lb = mock<Liquibase>()
    private val lbFactory = mock<(String, ResourceAccessor, Database) -> Liquibase> {
        on { invoke(any(), any(), any()) } doReturn (lb)
    }
    private val dbConnection = mock<DatabaseConnection>()
    private val db = mock<Database> {
        on { connection } doReturn (dbConnection)
    }
    private val dbFactory = mock<(connection: Connection) -> Database> {
        on { invoke(any()) } doReturn (db)
    }
    private val writer = mock<Writer>()

    private val migrator: LiquibaseSchemaMigrator =
        LiquibaseSchemaMigratorImpl(lbFactory, dbFactory)

    @Test
    fun `when updateDb create LB object`() {
        migrator.updateDb(connection, dbChange)
        verify(lbFactory).invoke(
            check {
                assertThat(it).startsWith("master-changelog")
            },
            check {
                assertThat(it).isInstanceOf(StreamResourceAccessor::class.java)
            },
            eq(db)
        )
    }

    @Test
    fun `when updateDb call Liquibase API`() {
        migrator.updateDb(connection, dbChange)
        verify(lb).update(any<Contexts>())
    }

    @Test
    fun `when createUpdateSql create LB object`() {
        migrator.createUpdateSql(connection, dbChange, writer)
        verify(lbFactory).invoke(
            check {
                assertThat(it).startsWith("master-changelog")
            },
            check {
                assertThat(it).isInstanceOf(StreamResourceAccessor::class.java)
            },
            eq(db)
        )
    }

    @Test
    fun `when createUpdateSql call Liquibase API`() {
        migrator.createUpdateSql(connection, dbChange, writer)
        verify(lb).update(any<Contexts>(), eq(writer))
    }

    @Test
    fun `when listUnrunChangeSets call liquibase API` (){
        migrator.listUnrunChangeSets(connection, dbChange)
        verify(lb).listUnrunChangeSets(any<Contexts>(), any())
    }
}
