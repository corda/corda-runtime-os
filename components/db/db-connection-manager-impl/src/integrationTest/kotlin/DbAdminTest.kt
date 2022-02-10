import com.typesafe.config.ConfigFactory
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.impl.DbAdminImpl
import net.corda.db.connection.manager.impl.DbConnectionsRepositoryImpl
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigFactory.Companion.SECRET_PASSPHRASE_KEY
import net.corda.libs.configuration.SmartConfigFactory.Companion.SECRET_SALT_KEY
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.testing.bundles.cats.Cat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import javax.persistence.EntityManagerFactory
import kotlin.random.Random

class DbAdminTest {
    private companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"
        private lateinit var entityManagerFactory: EntityManagerFactory
        private val configFactory = SmartConfigFactory.create(ConfigFactory.parseString("""
            ${SECRET_PASSPHRASE_KEY}=key
            ${SECRET_SALT_KEY}=salt
        """.trimIndent()))

        /**
         * Creates database and run config db migration
         * [entityManagerFactory].
         */
        @Suppress("Unused")
        @BeforeAll
        @JvmStatic
        private fun prepareDatabase() {

            // uncomment this to run the test against local Postgres
//            System.setProperty("postgresPort", "5432")

            val dbConfig = DbUtils.getEntityManagerConfiguration("configuration_db")

            val dbChange = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        DbSchema::class.java.packageName,
                        listOf(MIGRATION_FILE_LOCATION),
                        DbSchema::class.java.classLoader
                    )
                )
            )
            dbConfig.dataSource.connection.use { connection ->
                LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
            }
            entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
                "DB Admin integration test",
                ConfigurationEntities.classes.toList(),
                dbConfig
            )
        }

        @Suppress("Unused")
        @AfterAll
        @JvmStatic
        private fun cleanup() {
            entityManagerFactory.close()
        }
    }

    @Test
    fun `when createDbAndUser create schema and persist config`() {
        val dbc = DbConnectionsRepositoryImpl(EntityManagerFactoryFactoryImpl())
        val config = configFactory.create(DbUtils.createConfig("configuration_db"))
        dbc.initialise(config)
        val dba = DbAdminImpl(dbc)

        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        val random = Random.nextInt(1000)
        val persistenceUnitName = "test-$random"
        val schema = "test_schema_$random"

        // DDL
        dba.createDbAndUser(
            persistenceUnitName,
            schema,
            "u_ddl_$random",
            "test_password",
            config.getString(ConfigKeys.JDBC_URL),
            DbPrivilege.DDL,
            configFactory
        )
        val dbConfig = dbc.get(persistenceUnitName, DbPrivilege.DDL)
        assertThat(dbConfig?.connection).isNotNull
        assertDoesNotThrow { dbConfig?.connection?.close() }

        // validate the DDL User can create a table
        dbConfig!!.connection.use {
            it.createStatement().execute("CREATE TABLE superhero(name VARCHAR(255))")
            it.createStatement().execute("INSERT INTO superhero(name) VALUES('batman')")
            it.commit()
        }

        // validate the DDL user can run DB migrations
        val cl = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    Cat::class.java.packageName, listOf("migration/db.changelog-master.xml"), Cat::class.java.classLoader),
            ))
        LiquibaseSchemaMigratorImpl().updateDb(dbConfig.connection, cl)

        // DML
        dba.createDbAndUser(
            persistenceUnitName,
            schema,
            "u_dml_$random",
            "test_DML_password",
            config.getString(ConfigKeys.JDBC_URL),
            DbPrivilege.DML,
            configFactory
        )

        // validate the DML User can query a table
        dbConfig.connection.use {
            it.createStatement().execute("INSERT INTO $schema.superhero(name) VALUES('hulk')")
            it.commit()
            val heros = it
                .createStatement()
                .executeQuery("SELECT COUNT(*) as count FROM $schema.superhero")
            if(heros.next()) {
                assertThat(heros.getInt("count")).isGreaterThanOrEqualTo(2)
            }
        }
    }
}