package net.corda.sdk.vnode

import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.corda.db.admin.LiquibaseSchemaUpdater
import net.corda.db.admin.impl.LiquibaseSchemaUpdaterImpl
import java.io.File
import java.io.FileWriter
import java.sql.Connection
import java.sql.DriverManager

// TODO merge with DbSchemaGenerator
class VNodeDbSchemaGenerator(
    private val config: PlatformMigrationConfig = PlatformMigrationConfig(),
    private val liquibaseSchemaUpdater: LiquibaseSchemaUpdater = LiquibaseSchemaUpdaterImpl()
) {
    data class JdbcConnectionParams(
        val jdbcUrl: String,
        val user: String,
        val password: String,
    )

    private fun readHoldingIdsToMigrate(holdingIdFilename: String): List<String> {
        return mutableListOf<String>().apply {
            // Regex checks holdingId matches expected format and is one per line
            val regex = Regex("^[a-f0-9]{12}\$")
            config.lineReader(holdingIdFilename) {
                if (regex.matches(it)) {
                    add(it)
                } else if (it.isNotEmpty()) { // allow and ignore empty lines
                    throw IllegalArgumentException("Found invalid holding Id: $it")
                }
            }
        }
    }

    private data class LiquibaseFileAndSchema(val filename: String, val schemaPrefix: String)

    data class PlatformMigrationConfig(
        val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
        val lineReader: (String, (String) -> Unit) -> Unit = { filename, block ->
            File(filename).forEachLine { block(it) }
        },
        val liquibaseFactory: (String, Database) -> Liquibase = { file: String, database: Database ->
            Liquibase(file, ClassLoaderResourceAccessor(), database)
        },
        val jdbcConnectionFactory: (String?, String?, String?) -> Connection = { jdbcUrl, user, password ->
            DriverManager.getConnection(jdbcUrl, user, password)
        },
        val jdbcDatabaseFactory: (Connection) -> Database = { connection ->
            DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
        }
    )

    /**
     * TODO add kdoc
     */
    fun generateVNodeMigrationSqlFile(holdingIdFilename: String, outputFilename: String, jdbcConnectionParams: JdbcConnectionParams) {
        val holdingIdsToMigrate = readHoldingIdsToMigrate(holdingIdFilename) // TODO reduce nested calls

        config.writerFactory(outputFilename).use { fileWriter ->
            listOf(
                LiquibaseFileAndSchema("net/corda/db/schema/vnode-crypto/db.changelog-master.xml", "vnode_crypto_"),
                LiquibaseFileAndSchema("net/corda/db/schema/vnode-uniqueness/db.changelog-master.xml", "vnode_uniq_"),
                LiquibaseFileAndSchema("net/corda/db/schema/vnode-vault/db.changelog-master.xml", "vnode_vault_")
            ).forEach { fileAndSchema ->
                holdingIdsToMigrate.forEach { holdingId ->
                    generateSql(fileWriter, holdingId, fileAndSchema, jdbcConnectionParams)
                }
            }
        }
    }

    private fun generateSql(
        fileWriter: FileWriter,
        holdingId: String,
        fileAndSchema: LiquibaseFileAndSchema,
        jdbcConnectionParams: JdbcConnectionParams
    ) {
        val (jdbcUrl, user, password) = jdbcConnectionParams
//        withPluginClassLoader { TODO wrap inside the run()
        val connection = config.jdbcConnectionFactory(jdbcUrl, user, password)
        val database = config.jdbcDatabaseFactory(connection).apply {
            val schemaName = fileAndSchema.schemaPrefix + holdingId
            defaultSchemaName = schemaName // our tables
            liquibaseSchemaName = schemaName // liquibase tracking tables
        }

        connection.use {
            val lb = config.liquibaseFactory(fileAndSchema.filename, database)
            liquibaseSchemaUpdater.update(lb, fileWriter)
        }
//        }
    }
}
