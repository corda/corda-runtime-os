import com.typesafe.config.ConfigFactory
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.impl.DbAdminImpl
import net.corda.db.connection.manager.impl.DbConnectionManagerImpl
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.db.testkit.DbUtils.getPostgresDatabase
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigFactory.Companion.SECRET_PASSPHRASE_KEY
import net.corda.libs.configuration.SmartConfigFactory.Companion.SECRET_SALT_KEY
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.impl.JpaEntitiesRegistryImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.testing.bundles.cats.Cat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
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
            // System.setProperty("postgresPort", "5432")

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

    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }

    @Test
    fun `when createDbAndUser create schema and persist config`() {
        val entitiesRegistry = JpaEntitiesRegistryImpl()
        val dbm = DbConnectionManagerImpl(lifecycleCoordinatorFactory, EntityManagerFactoryFactoryImpl(), entitiesRegistry)
        val config = configFactory.create(DbUtils.createConfig("configuration_db"))
        entitiesRegistry.register(
            CordaDb.CordaCluster.persistenceUnitName,
            ConfigurationEntities.classes
        )
        dbm.initialise(config)
        val dba = DbAdminImpl(dbm)

        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        val random = Random.nextLong(Long.MAX_VALUE)
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
        val dbConfig = dbm.getDataSource(persistenceUnitName, DbPrivilege.DDL)
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

    @Test
    fun `DML user can use table created by DDL user`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        testDmlUserCanUseTableCreatedByDdlUser()
    }

    @Test
    fun `when DB admin is not superuser, DML user can use table created by DDL user`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        // Create admin user that is not superuser
        val random = Random.nextLong(Long.MAX_VALUE)
        val adminUser = "test_admin_$random"
        val adminPassword = "test_admin_password_$random"
        createAdminUser(getPostgresDatabase(), adminUser, adminPassword)

        testDmlUserCanUseTableCreatedByDdlUser(adminUser, adminPassword)
    }

    /**
     * Creates DDL and DML users using the admin user. Tests that DML user can use table created by DDL user.
     * @param adminUser Admin user. If null, default value set in [DbUtils] will be used.
     * @param adminPassword Admin password. If null, default value set in [DbUtils] will be used.
     */
    private fun testDmlUserCanUseTableCreatedByDdlUser(adminUser: String? = null, adminPassword: String? = null) {
        // Create DbAdmin
        val entitiesRegistry = JpaEntitiesRegistryImpl()
        val dbm = DbConnectionManagerImpl(lifecycleCoordinatorFactory, EntityManagerFactoryFactoryImpl(), entitiesRegistry)
        val config = configFactory.create(DbUtils.createConfig("configuration_db", adminUser, adminPassword))
        entitiesRegistry.register(
            CordaDb.CordaCluster.persistenceUnitName,
            ConfigurationEntities.classes
        )
        dbm.initialise(config)
        val dba = DbAdminImpl(dbm)

        val random = Random.nextLong(Long.MAX_VALUE)
        val schema = "test_schema_$random"
        val ddlUser = "user_ddl_$random"
        val dmlUser = "user_dml_$random"
        val password = "pwd_$random"

        // Create DDL user
        dba.createDbAndUser(schema, ddlUser, password, DbPrivilege.DDL)
        assertThat(dba.userExists(ddlUser)).isTrue

        // Create DML user
        dba.createDbAndUser(schema, dmlUser, password, DbPrivilege.DML, ddlUser)
        assertThat(dba.userExists(dmlUser)).isTrue

        // Validate that DDL user can create a table
        val ddlDataSource = DbUtils.createPostgresDataSource(ddlUser, password)
        ddlDataSource.use { dataSource ->
            dataSource.connection.use {
                it.createStatement().execute("CREATE TABLE $schema.test_table (message VARCHAR(64))")
                it.createStatement().execute("INSERT INTO $schema.test_table VALUES('test')")
                it.commit()
            }
        }

        // Validate that DML user can select from table
        val dmlDataSource = DbUtils.createPostgresDataSource(dmlUser, password)
        dmlDataSource.use { dataSource ->
            dataSource.connection.use {
                it.createStatement().execute("INSERT INTO $schema.test_table VALUES('test2')")
                it.commit()
                val rows = it
                    .createStatement()
                    .executeQuery("SELECT COUNT(*) as count FROM $schema.test_table")
                if (rows.next()) {
                    assertThat(rows.getInt("count")).isEqualTo(2)
                }
            }
        }
    }

    @Test
    fun `recreated DDL user can create table`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        recreatedDdlUserCanCreateTable()
    }

    @Test
    fun `when DB admin is not superuser, recreated DDL user can create table`() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        // Create admin user that is not superuser
        val random = Random.nextLong(Long.MAX_VALUE)
        val adminUser = "test_admin_$random"
        val adminPassword = "test_admin_password_$random"
        createAdminUser(getPostgresDatabase(), adminUser, adminPassword)

        recreatedDdlUserCanCreateTable(adminUser, adminPassword)
    }

    /**
     * Creates DDL user using the admin user. Tests that DDL user can be recreated and can use table created by DDL user.
     *
     * @param adminUser Admin user. If null, default value set in [DbUtils] will be used.
     * @param adminPassword Admin password. If null, default value set in [DbUtils] will be used.
     */
    private fun recreatedDdlUserCanCreateTable(adminUser: String? = null, adminPassword: String? = null) {
        // Create DbAdmin
        val entitiesRegistry = JpaEntitiesRegistryImpl()
        val dbm = DbConnectionManagerImpl(lifecycleCoordinatorFactory, EntityManagerFactoryFactoryImpl(), entitiesRegistry)
        val config = configFactory.create(DbUtils.createConfig("configuration_db", adminUser, adminPassword))
        entitiesRegistry.register(
            CordaDb.CordaCluster.persistenceUnitName,
            ConfigurationEntities.classes
        )
        dbm.initialise(config)
        val dba = DbAdminImpl(dbm)

        val random = Random.nextLong(Long.MAX_VALUE)
        val schema = "test_schema_$random"
        val table = "test_table"
        val ddlUser = "user_ddl_$random"
        val password = "pwd_ddl_$random"

        // Create DDL user
        dba.createDbAndUser(schema, ddlUser, password, DbPrivilege.DDL)
        assertThat(dba.userExists(ddlUser)).isTrue

        // Validate that DDL user can create a table
        createTable(schema, table, ddlUser, password)

        // Recreate user
        dba.deleteSchema(schema)
        dba.deleteUser(ddlUser)
        assertThat(dba.userExists(ddlUser)).isFalse

        val newPassword = "new_pwd_ddl_$random"
        dba.createDbAndUser(schema, ddlUser, newPassword, DbPrivilege.DDL)
        assertThat(dba.userExists(ddlUser)).isTrue

        // Validate that DDL user can create a table
        createTable(schema, table, ddlUser, newPassword)
    }

    /**
     * Creates admin DB user that is not a superuser.
     *
     * @param database Database name
     * @param newUser New user's name
     * @param newPassword New user's password
     */
    private fun createAdminUser(database:String, newUser: String, newPassword: String) {
        DbUtils.createPostgresDataSource().use { dataSource ->
            dataSource.connection.use {
                it.createStatement().execute("CREATE USER $newUser WITH PASSWORD '$newPassword'")
                it.createStatement().execute("ALTER ROLE $newUser NOSUPERUSER CREATEDB CREATEROLE INHERIT LOGIN")
                it.createStatement().execute("GRANT CREATE ON DATABASE \"$database\" TO $newUser")
                it.commit()
            }
        }
    }

    /**
     * Creates table in database.
     *
     * @param schema Schema name
     * @param table Table name
     * @param user User
     * @param password Password
     */
    private fun createTable(schema:String, table: String, user: String, password: String) {
        DbUtils.createPostgresDataSource(user, password).use { dataSource ->
            dataSource.connection.use {
                it.createStatement().execute(
                    "CREATE TABLE $schema.$table (message VARCHAR(64))")
                it.commit()
            }
        }
    }
}