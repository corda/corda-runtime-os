package net.corda.db.admin.impl

import net.corda.db.admin.LiquibaseSchemaMigrator.Companion.PUBLIC_SCHEMA
import net.corda.db.core.InMemoryDataSourceFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.StringWriter

class LiquibaseSchemaMigratorImplTest {
    val ds = InMemoryDataSourceFactory().create("test-db")
    val cl1 = ClassloaderChangeLog(
        linkedSetOf(
            ClassloaderChangeLog.ChangeLogResourceFiles(
                this.javaClass.packageName, listOf("migration/db.changelog-master.xml")),
            ClassloaderChangeLog.ChangeLogResourceFiles(
                this.javaClass.packageName, listOf("migration/db.changelog-master2.xml"), this.javaClass.classLoader)
        )
    )
    val cl2 = ClassloaderChangeLog(
        linkedSetOf(
            ClassloaderChangeLog.ChangeLogResourceFiles(
                this.javaClass.packageName, listOf("migration/db.changelog-master3.xml")),
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
        ds.connection.use {
            it.prepareStatement("CREATE SCHEMA IF NOT EXISTS bobby;").execute()
            it.prepareStatement("CREATE TABLE bobby.foo(LastName varchar(255));").execute()
        }

        val lbm = LiquibaseSchemaMigratorImpl()

        lbm.updateDb(ds.connection, cl1, PUBLIC_SCHEMA)
        lbm.updateDb(ds.connection, cl2, PUBLIC_SCHEMA)

        val tables = mutableListOf<String>()
        ds.connection.use {
            it.metaData.getTables(null, null, null, arrayOf("TABLE")).use { rs ->
                while (rs.next()) {
                    val fullTableName = "${rs.getString("TABLE_SCHEM")}.${rs.getString("TABLE_NAME")}"
                    println("TABLE $fullTableName exists")
                    tables.add(fullTableName.toLowerCase())
                }
            }
        }

        assertThat(tables).containsAll(
            listOf(
                "public.test_table", "public.another_table", "public.generic_table",
                "public.databasechangelog", "public.databasechangeloglock",
                "another_schema.test_table_in_other_schema"
            )
        )
        assertThat(tables).doesNotContain("postgres_table")
    }

    @Test
    fun `when createUpdateSql generate DB schema`() {
        val lbm = LiquibaseSchemaMigratorImpl()
        val writer1 = StringWriter()
        lbm.createUpdateSql(ds.connection, cl1, writer1, PUBLIC_SCHEMA)
        val sql1 = writer1.toString()

        // Create first script
        println("SQL Script:")
        println(sql1)
        assertThat(sql1.toLowerCase())
            .contains("create table public.test_table")
            .contains("create table public.another_table")
            .contains("create table public.generic_table")
            .contains("create table public.databasechangelog")
            .contains("create table public.databasechangeloglock")
            .doesNotContain("create table public.postgres_table")

        // run it
        ds.connection.use { db ->
            sql1.lines()
                .filter {
                    it.isNotBlank() && !it.startsWith("--")
                }
                .forEach {
                db.createStatement().execute(it)
            }
        }

        // Create another script
        val writer2 = StringWriter()
        lbm.createUpdateSql(ds.connection, cl2, writer2, PUBLIC_SCHEMA)
        val sql2 = writer2.toString()
        println("Second SQL Script:")
        println(sql2)
        assertThat(sql2.toLowerCase())
            .contains("create schema if not exists another_schema;")
            .contains("create table another_schema.test_table_in_other_schema")
            .doesNotContain("create table public.databasechangelog")
            .doesNotContain("create table public.databasechangeloglock")

        // and run it
        ds.connection.use { db ->
            sql2.lines()
                .filter {
                    it.isNotBlank() && !it.startsWith("--")
                }
                .forEach {
                    db.createStatement().execute(it)
                }
        }
    }
}
