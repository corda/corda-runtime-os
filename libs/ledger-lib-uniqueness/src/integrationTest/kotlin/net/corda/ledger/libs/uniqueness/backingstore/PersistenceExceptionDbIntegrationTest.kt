package net.corda.ledger.libs.uniqueness.backingstore

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.ledger.libs.uniqueness.backingstore.impl.SqlPersistenceExceptionCategorizerImpl
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.PersistenceExceptionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.sql.SQLException
import java.util.UUID

// These tests validate the exception types (returned from a real DB) used in SqlPersistenceExceptionCategorizerImpl
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceExceptionDbIntegrationTest {
    private val dbConfig: EntityManagerConfiguration

    init {
        // uncomment this to run the test against local Postgres
//        System.setProperty("databaseType", "POSTGRES")

        dbConfig = DbUtils.getEntityManagerConfiguration("uniqueness_session")

        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/vnode-uniqueness/db.changelog-master.xml"),
                    DbSchema::class.java.classLoader
                )
            )
        )
        dbConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }

        dbConfig.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS superman(
                        name VARCHAR(100) NOT NULL PRIMARY KEY, 
                        age INT NOT NULL,
                        phone_number VARCHAR(50) NOT NULL,
                        UNIQUE(phone_number)
                    );
                    """.trimIndent()
                )
                connection.commit()
            }
        }
    }

    private val exceptionCategorizer = SqlPersistenceExceptionCategorizerImpl()

    @Test
    fun `primary key constraint violation`() {
        val e = assertThrows<SQLException> {
            dbConfig.dataSource.connection.use { connection ->
                val id = UUID.randomUUID()
                connection.createStatement().use { statement ->
                    statement.execute("INSERT INTO superman(name, age, phone_number) VALUES('$id', 10, 'p${UUID.randomUUID()}');")
                    statement.execute("INSERT INTO superman(name, age, phone_number) VALUES('$id', 11, 'p${UUID.randomUUID()}');")
                    connection.commit()
                }
            }
        }

        assertThat(exceptionCategorizer.categorize(e)).isEqualTo(PersistenceExceptionType.DATA_RELATED)
    }

    @Test
    fun `unique key constraint violation`() {
        val e = assertThrows<SQLException> {
            val phone = "p-${UUID.randomUUID()}"
            dbConfig.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("INSERT INTO superman(name, age, phone_number) VALUES('${UUID.randomUUID()}', 10, '$phone');")
                    statement.execute("INSERT INTO superman(name, age, phone_number) VALUES('${UUID.randomUUID()}', 11, '$phone');")
                    connection.commit()
                }
            }
        }

        assertThat(exceptionCategorizer.categorize(e)).isEqualTo(PersistenceExceptionType.DATA_RELATED)
    }

    @Test
    fun `null constraint violation`() {
        val e = assertThrows<SQLException> {
            dbConfig.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "INSERT INTO superman(name, age, phone_number) VALUES('${UUID.randomUUID()}', NULL, '${UUID.randomUUID()}');"
                    )
                    connection.commit()
                }
            }
        }

        assertThat(exceptionCategorizer.categorize(e)).isEqualTo(PersistenceExceptionType.DATA_RELATED)
    }

    @Test
    fun `invalid SQL`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        val e = assertThrows<SQLException> {
            dbConfig.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "INSERPP INTO superman(name, age, phone_number) VALUES('${UUID.randomUUID()}', NULL, '${UUID.randomUUID()}');"
                    )
                    connection.commit()
                }
            }
        }

        assertThat(exceptionCategorizer.categorize(e)).isEqualTo(PersistenceExceptionType.FATAL)
    }

    @Test
    fun `invalid field`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        val e = assertThrows<SQLException> {
            dbConfig.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "INSERT INTO superman(namedddd, age, phone_number) VALUES('${UUID.randomUUID()}', NULL, '${UUID.randomUUID()}');"
                    )
                    connection.commit()
                }
            }
        }

        assertThat(exceptionCategorizer.categorize(e)).isEqualTo(PersistenceExceptionType.FATAL)
    }

    @Test
    fun `invalid table`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        val e = assertThrows<SQLException> {
            dbConfig.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "INSERT INTO supermanssss(name, age, phone_number) VALUES('${UUID.randomUUID()}', NULL, '${UUID.randomUUID()}');"
                    )
                    connection.commit()
                }
            }
        }

        assertThat(exceptionCategorizer.categorize(e)).isEqualTo(PersistenceExceptionType.FATAL)
    }
}
