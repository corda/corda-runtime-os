package net.corda.cli.plugins.dbconfig

import net.corda.sdk.bootstrap.dbconfig.DbSchemaGenerator
import net.corda.sdk.bootstrap.dbconfig.DbSchemaGenerator.Companion.DEFAULT_CHANGELOG_PATH
import net.corda.sdk.bootstrap.dbconfig.DbSchemaGenerator.Companion.DEFAULT_SCHEMA_OPTIONS
import net.corda.sdk.bootstrap.dbconfig.DbSchemaGenerator.Companion.SCHEMA_OPTIONS
import picocli.CommandLine
import java.nio.file.Path

@CommandLine.Command(
    name = "spec",
    description = ["Does database schema generation from liquibase. Can run offline or connect to a live database for " +
            "migration to a new version."],
    mixinStandardHelpOptions = true
)
class Spec(private val config: DbSchemaGenerator.SpecConfig = DbSchemaGenerator.SpecConfig()) : Runnable {
    @CommandLine.Option(
        names = ["--change-log"],
        description = ["Path and filename of the databasechangelog CSV file which is created by Liquibase in offline" +
                "mode. Defaults to '${DEFAULT_CHANGELOG_PATH}'"]
    )
    var providedDatabaseChangeLogFile = Path.of(DEFAULT_CHANGELOG_PATH)

    @CommandLine.Option(
        names = ["-c", "--clear-change-log"],
        description = ["Automatically delete the changelogCSV to force generation of the sql files"]
    )
    var providedClearChangeLog: Boolean? = false

    @CommandLine.Option(
        names = ["-s", "--schemas"],
        description = ["List of sql files to generate. Default is files for all schemas except 'message bus' which is " +
                "not used in a distributed Corda Cluster deployment, only in all in-process worker deployments like " +
                "the Combined Worker. Options are: $SCHEMA_OPTIONS"],
        split = ","
    )
    var providedSchemasToGenerate: List<String> = DEFAULT_SCHEMA_OPTIONS

    @CommandLine.Option(
        names = ["-g", "--generate-schema-sql"],
        description = ["By default sql files generated are schema-less, it is the responsibility of the db admin to apply " +
                "these files to the correct schema. This option adds schema creation to the sql files instead. The schema " +
                "names should be passed as a list, where each item takes the form 'schema-type:schema-name'. Schema-types " +
                "are taken from: $SCHEMA_OPTIONS. E.g \"config:my-config-schema,crypto:my-crypto-schema\" where config tables " +
                "would end up in a schema called my-config-schema and crypto tables would end up in a schema called " +
                "my-crypto-schema. Any schemas not specified will take the default name, which is the same as schema-type. To " +
                "generate schemas using all default names pass \"\" as the value."],
        split = ","
    )
    var providedGenerateSchemaSql: List<String>? = null

    @CommandLine.Option(
        names = ["-l", "--location"],
        description = ["Directory to write all files to"]
    )
    var providedOutputDir: String = "."

    @CommandLine.Option(
        names = ["--jdbc-url"],
        description = ["JDBC Url of database. If not specified runs in offline mode"]
    )
    var providedJdbcUrl: String? = null

    @CommandLine.Option(
        names = ["-u", "--user"],
        description = ["Database username"]
    )
    var providedUser: String? = null

    @CommandLine.Option(
        names = ["-p", "--password"],
        description = ["Database password"]
    )
    var providedPassword: String? = null

    override fun run() {
        val generator = DbSchemaGenerator(config = config).apply {
            jdbcUrl = providedJdbcUrl
            user = providedUser
            password = providedPassword
            clearChangeLog = providedClearChangeLog ?: false
            databaseChangeLogFile = providedDatabaseChangeLogFile
            classLoaderWorkaround = DatabaseBootstrapAndUpgrade.classLoader
        }

        generator.generateSqlFilesForSchemas(
            schemasToGenerate = providedSchemasToGenerate,
            generateSchemaSql = providedGenerateSchemaSql,
            outputDir = providedOutputDir
        )
    }
}
