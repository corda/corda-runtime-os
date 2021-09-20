package net.corda.db.admin.impl

import net.corda.db.admin.ClassloaderChangeLog
import net.corda.db.core.InMemoryDataSourceFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.StringWriter

class LiquibaseSchemaMigratorImplTest {
    val ds = InMemoryDataSourceFactory().create("test-db")
    val cl = ClassloaderChangeLog(
        linkedSetOf(
            ClassloaderChangeLog.ChangeLogResourceFiles(
                this.javaClass.packageName, listOf("migration/db.changelog-master.xml")),
            ClassloaderChangeLog.ChangeLogResourceFiles(
                this.javaClass.packageName, listOf("migration/db.changelog-master2.xml"), this.javaClass.classLoader)
        )
    )

    @AfterEach
    fun `clear db`() {
        ds.connection.use {
            it.prepareStatement("DROP SCHEMA PUBLIC CASCADE").execute()
        }
    }

    @Test
    fun `when updateDb create DB schema`() {
        val lbm = LiquibaseSchemaMigratorImpl()

        lbm.updateDb(ds.connection, cl)

        var tables = mutableListOf<String>()
        ds.connection.use {
            val rs = it.metaData.getTables(null, null, null, arrayOf("TABLE"))
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase())
            }
            rs.close()
        }

        assertThat(tables).containsAll(
            listOf(
                "test_table", "another_table", "generic_table",
                "databasechangelog", "databasechangeloglock"
            )
        )
        assertThat(tables).doesNotContain("postgres_table")
    }

    @Test
    fun `when createUpdateSql generate DB schema`() {
        val writer = StringWriter()
        val lbm = LiquibaseSchemaMigratorImpl()
        lbm.createUpdateSql(ds.connection, cl, writer)

        val sql = writer.toString().toLowerCase()
        assertThat(sql).contains("create table public.test_table")
        assertThat(sql).contains("create table public.another_table")
        assertThat(sql).contains("create table public.generic_table")
        assertThat(sql).doesNotContain("create table public.postgres_table")
    }
}
