package net.corda.application.dbsetup


import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.config.impl.createCryptoSmartConfigFactory
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.core.DbPrivilege
import net.corda.db.core.OSGiDataSourceFactory
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID


// TODO This class bootstraps database, duplicating functionality available via CLI
// As it duplicates some classes from tools/plugins/initial-config/src/main/kotlin/net/corda/cli/plugin/, it requires
// refactoring, but first we need an input from the DevX team, whether this is the right approach or developers should
// use CLI instead

@Suppress("LongParameterList")
class PostgresDbSetup(
    private val dbUrl: String,
    private val superUser: String,
    private val superUserPassword: String,
    private val dbAdmin: String,
    private val dbAdminPassword: String,
    private val dbName: String,
    private val secretsSalt: String,
    private val secretsPassphrase: String,
): DbSetup {
    
    companion object {
        private const val DB_DRIVER = "org.postgresql.Driver"

        private val changelogFiles = mapOf(
            "net/corda/db/schema/config/db.changelog-master.xml" to null,
            "net/corda/db/schema/messagebus/db.changelog-master.xml" to null,
            "net/corda/db/schema/rbac/db.changelog-master.xml" to null,
            "net/corda/db/schema/crypto/db.changelog-master.xml" to "CRYPTO"
        )

        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val dbAdminUrl by lazy {
        "$dbUrl?user=$dbAdmin&password=$dbAdminPassword"
    }

    private val dbSuperUserUrl by lazy {
        "$dbUrl?user=$superUser&password=$superUserPassword"
    }

    override fun run() {
        if (!dbInitialised()) {
            log.info("Initialising DB.")
            initDb()
            runDbMigration()
            initConfiguration("corda-rbac", "rbac_user_$dbName", "rbac_password", dbUrl)
            initConfiguration("corda-crypto", "crypto_user_$dbName", "crypto_password", "$dbUrl?currentSchema=CRYPTO")
            createUserConfig("admin", "admin")
            createDbUsersAndGrants()
            createCryptoConfig()
        } else {
            log.info("Table config.config exists in $dbSuperUserUrl, skipping DB initialisation.")
        }
    }

    private fun superUserConnection() =
        OSGiDataSourceFactory.create(DB_DRIVER, dbSuperUserUrl, superUser, superUserPassword).connection

    private fun adminConnection() =
        OSGiDataSourceFactory.create(DB_DRIVER, dbAdminUrl, dbAdmin, dbAdminPassword).connection

    private fun dbInitialised(): Boolean {
        superUserConnection()
            .use { connection ->
                connection.createStatement().executeQuery(
                    "SELECT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'config' AND tablename = 'config');"
                )
                    .use {
                        if (it.next()) {
                            return it.getBoolean(1)
                        }
                    }
            }
        return false
    }

    private fun initDb() {
        log.info("Create user $dbAdmin in $dbName in $dbSuperUserUrl.")
        superUserConnection()
            .use { connection ->
                connection.createStatement().execute(
                    // NOTE: this is different to the cli as this is set up to be using the official postgres image
                    //   instead of the Bitnami. The official image doesn't already have the "user" user.
                    """
                        CREATE USER "$dbAdmin" WITH ENCRYPTED PASSWORD '$dbAdminPassword';
                        ALTER ROLE "$dbAdmin" NOSUPERUSER CREATEDB CREATEROLE INHERIT LOGIN;
                        ALTER DATABASE "$dbName" OWNER TO "$dbAdmin";
                        ALTER SCHEMA public OWNER TO "$dbAdmin";
                    """.trimIndent())
            }
    }

    private fun runDbMigration() {
        log.info("Run DB migrations in $dbAdminUrl.")
        adminConnection()
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
        adminConnection()
            .use { connection ->
                connection.createStatement().execute("CREATE SCHEMA IF NOT EXISTS $schema;")
            }
    }

    private fun initConfiguration(connectionName: String, username: String, password :String, jdbcUrl:String) {
        log.info("Initialise configuration for $connectionName ($jdbcUrl).")
        val secretsService = EncryptionSecretsServiceImpl(secretsPassphrase, secretsSalt)

        val dbConnectionConfig = DbConnectionConfig(
            id = UUID.randomUUID(),
            name = connectionName,
            privilege = DbPrivilege.DML,
            updateTimestamp = Instant.now(),
            updateActor = "Setup Script",
            description = "Initial configuration - autogenerated by setup script",
            config = createDbConfig(jdbcUrl, username, password, secretsService)
        ).also { it.version = 0 }

        adminConnection()
            .use { connection ->
                connection.createStatement().execute(dbConnectionConfig.toInsertStatement())
            }
    }

    private fun createUserConfig(user: String, password: String) {
        log.info("Create user config for $user")
        adminConnection()
            .use { connection ->
                connection.createStatement().execute(buildRbacConfigSql(user, password, "Setup Script"))
            }
    }

    private fun createDbUsersAndGrants() {
        val sql = """
            CREATE SCHEMA IF NOT EXISTS CRYPTO;
            
            CREATE USER rbac_user_$dbName WITH ENCRYPTED PASSWORD 'rbac_password';
            GRANT USAGE ON SCHEMA RBAC to rbac_user_$dbName;
            GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA RBAC to rbac_user_$dbName;
            CREATE USER crypto_user_$dbName WITH ENCRYPTED PASSWORD 'crypto_password';
            GRANT USAGE ON SCHEMA CRYPTO to crypto_user_$dbName;
            GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA CRYPTO to crypto_user_$dbName;
        """.trimIndent()

        adminConnection()
            .use { connection ->
                connection.createStatement().execute(sql)
            }
    }

    private fun createCryptoConfig() {
        val random = SecureRandom()
        val config = createCryptoSmartConfigFactory(
            KeyCredentials(
                salt = secretsSalt,
                passphrase = secretsPassphrase
            )
        ).createDefaultCryptoConfig(
            KeyCredentials(
                passphrase = random.randomString(),
                salt = random.randomString()
            )
        ).root().render(ConfigRenderOptions.concise())

        val entity = ConfigEntity(
            section = CRYPTO_CONFIG,
            config = config,
            schemaVersionMajor = 1,
            schemaVersionMinor = 0,
            updateTimestamp = Instant.now(),
            updateActor = "init",
            isDeleted = false
        ).apply {
            version = 0
        }

        adminConnection()
            .use { connection ->
                connection.createStatement().execute(entity.toInsertStatement())
            }
    }

    private fun createDbConfig(
        jdbcUrl: String,
        username: String,
        password: String,
        secretsService: SecretsCreateService
    ): String {
        return "{\"database\":{" +
                "\"jdbc\":" +
                "{\"url\":\"$jdbcUrl\"}," +
                "\"pass\":${createSecureConfig(secretsService, password)}," +
                "\"user\":\"$username\"}}"
    }

    private fun createSecureConfig(secretsService: SecretsCreateService, value: String): String {
        return secretsService.createValue(value).root().render(ConfigRenderOptions.concise())
    }

    private fun SecureRandom.randomString(length: Int = 32): String = ByteArray(length).let {
        this.nextBytes(it)
        Base64.getEncoder().encodeToString(it)
    }
}
