package net.corda.application.dbsetup


import com.typesafe.config.ConfigRenderOptions
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.v5.base.util.contextLogger
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

// TODO This class bootstraps database, duplicating functionality available via CLI
// As it duplicates some classes from tools/plugins/initial-config/src/main/kotlin/net/corda/cli/plugin/, it requires
// refactoring, but first we need an input from the DevX team, whether this is the right approach or developers should
// use CLI instead

class PostgresDbSetup: DbSetup {
    
    companion object {
        private const val DB_DRIVER = "org.postgresql.Driver"
        private const val DB_HOST = "localhost"
        private const val DB_PORT = "5432"
        private const val DB_NAME = "cordacluster"
        private const val DB_URL = "jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME"
        private const val DB_SUPERUSER_DEFAULT = "postgres"
        private const val DB_SUPERUSER_PASSWORD_DEFAULT = "password"
        private const val DB_ADMIN = "user"
        private const val DB_ADMIN_PASSWORD = "password"
        private const val DB_ADMIN_URL = "$DB_URL?user=$DB_ADMIN&password=$DB_ADMIN_PASSWORD"
        private const val SECRETS_SALT = "salt"
        private const val SECRETS_PASSWORD = "password"

        private val changelogFiles = mapOf(
            "net/corda/db/schema/config/db.changelog-master.xml" to null,
            "net/corda/db/schema/messagebus/db.changelog-master.xml" to null,
            "net/corda/db/schema/rbac/db.changelog-master.xml" to null,
            "net/corda/db/schema/cluster-certificates/db.changelog-master.xml" to null,
            "net/corda/db/schema/crypto/db.changelog-master.xml" to "CRYPTO"
        )

        private val log = contextLogger()
    }


    private val dbSuperUserUrl by lazy {
        val superUser = System.getenv("CORDA_DEV_POSTGRES_USER") ?: DB_SUPERUSER_DEFAULT
        val superUserPassword = System.getenv("CORDA_DEV_POSTGRES_PASSWORD") ?: DB_SUPERUSER_PASSWORD_DEFAULT

        "$DB_URL?user=$superUser&password=$superUserPassword"
    }

    override fun run() {
        log.info("Bootstrap Postgres DB.")
        Class.forName(DB_DRIVER)

        if (!dbInitialised()) {
            log.info("Initialising DB.")
            initDb()
            runDbMigration()
            initConfiguration("corda-rbac", "rbac_user", "rbac_password", DB_URL)
            initConfiguration("corda-crypto", "crypto_user", "crypto_password", "$DB_URL?currentSchema=CRYPTO")
            createUserConfig("admin", "admin")
            createDbUsersAndGrants()
        }
    }

    private fun dbInitialised(): Boolean {
        DriverManager
            .getConnection(dbSuperUserUrl)
            .use { connection ->
                connection.createStatement().executeQuery(
                    "SELECT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'config' AND tablename = 'config');")
                .use {
                    if (it.next()) {
                        log.info("Table config.config exists in $dbSuperUserUrl, skipping DB initialisation.")
                        return it.getBoolean(1)
                    }
                }
            }
        return false
    }

    private fun initDb() {
        log.info("Create user $DB_ADMIN in $DB_NAME in $dbSuperUserUrl.")
        DriverManager
            .getConnection(dbSuperUserUrl)
            .use { connection ->
                connection.createStatement().execute(
                    // NOTE: this is different to the cli as this is set up to be using the official postgres image
                    //   instead of the Bitnami. The official image doesn't already have the "user" user.
                    """
                        CREATE USER "$DB_ADMIN" WITH ENCRYPTED PASSWORD '$DB_ADMIN_PASSWORD';
                        ALTER ROLE "$DB_ADMIN" NOSUPERUSER CREATEDB CREATEROLE INHERIT LOGIN;
                        ALTER DATABASE "$DB_NAME" OWNER TO "$DB_ADMIN";
                        ALTER SCHEMA public OWNER TO "$DB_ADMIN";
                    """.trimIndent())
            }
    }

    private fun runDbMigration() {
        log.info("Run DB migrations in $DB_ADMIN_URL.")
        DriverManager
            .getConnection(DB_ADMIN_URL)
            .use { connection ->
                changelogFiles.forEach { (file, schema) ->
                    val changeLogResourceFiles = setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
                        ClassloaderChangeLog.ChangeLogResourceFiles(
                            klass.packageName,
                            listOf(file),
                            klass.classLoader
                        )
                    }
                    val dbChange = ClassloaderChangeLog(changeLogResourceFiles)
                    val schemaMigrator = LiquibaseSchemaMigratorImpl()
                    if (schema != null) {
                        createDbSchema(schema)
                        connection.prepareStatement("SET search_path TO $schema;").execute()
                        schemaMigrator.updateDb(connection, dbChange, schema)
                    } else {
                        schemaMigrator.updateDb(connection, dbChange)
                    }
                }
            }
    }

    private fun createDbSchema(schema: String) {
        log.info("Create SCHEMA $schema.")
        DriverManager
            .getConnection(DB_ADMIN_URL)
            .use { connection ->
                connection.createStatement().execute("CREATE SCHEMA IF NOT EXISTS $schema;")
            }
    }

    private fun initConfiguration(connectionName: String, username: String, password :String, jdbcUrl:String) {
        log.info("Initialise configuration for $connectionName ($jdbcUrl).")
        val secretsService = EncryptionSecretsServiceImpl(SECRETS_PASSWORD, SECRETS_SALT)

        val dbConnectionConfig = DbConnectionConfig(
            id = UUID.randomUUID(),
            name = connectionName,
            privilege = DbPrivilege.DML,
            updateTimestamp = Instant.now(),
            updateActor = "Setup Script",
            description = "Initial configuration - autogenerated by setup script",
            config = createDbConfig(jdbcUrl, username, password, secretsService)
        ).also { it.version = 0 }

        DriverManager
            .getConnection(DB_ADMIN_URL)
            .use { connection ->
                connection.createStatement().execute(dbConnectionConfig.toInsertStatement())
            }
    }

    private fun createUserConfig(user: String, password: String) {
        log.info("Create user config for $user")
        DriverManager
            .getConnection(DB_ADMIN_URL)
            .use { connection ->
                connection.createStatement().execute(buildRbacConfigSql(user, password, "Setup Script"))
            }
    }

    private fun createDbUsersAndGrants() {
        val sql = """
            CREATE SCHEMA IF NOT EXISTS CRYPTO;
            
            CREATE USER rbac_user WITH ENCRYPTED PASSWORD 'rbac_password';
            GRANT USAGE ON SCHEMA RPC_RBAC to rbac_user;
            GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA RPC_RBAC to rbac_user;
            CREATE USER crypto_user WITH ENCRYPTED PASSWORD 'crypto_password';
            GRANT USAGE ON SCHEMA CRYPTO to crypto_user;
            GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA CRYPTO to crypto_user;
        """.trimIndent()

        DriverManager
            .getConnection(DB_ADMIN_URL)
            .use { connection ->
                connection.createStatement().execute(sql)
            }
    }

    private fun createDbConfig(jdbcUrl: String, username: String, password: String, secretsService: SecretsCreateService): String {
        return "{\"database\":{" +
                "\"jdbc\":" +
                "{\"url\":\"$jdbcUrl\"}," +
                "\"pass\":${createSecureConfig(secretsService, password)}," +
                "\"user\":\"$username\"}}"
    }

    private fun createSecureConfig(secretsService: SecretsCreateService, value: String): String {
        return secretsService.createValue(value).root().render(ConfigRenderOptions.concise())
    }
}