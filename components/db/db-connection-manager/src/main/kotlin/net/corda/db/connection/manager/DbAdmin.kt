package net.corda.db.connection.manager

import net.corda.db.core.DbPrivilege
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Db admin component to manage creation of "logical" DB (Schemas) and users.
 * This class is abstract to force users to bind to a data source in a type safe way. That is, instances of that
 * subclass type can be passed around and not just generic DbAdmin types, because those could be mixed up and be bound
 * to the wrong data source and then clients of the class would be talking to the wrong database.
 */
abstract class DbAdmin {

    private val jdbcRootUrl by lazy {
        withDbConnection { connection ->
            checkNotNull(connection.metaData.url) { "Cannot retrieve connection URL from database Connection" }.split(
                "?"
            ).first()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Subclasses must provide a data source, the guarantees are:
     * 1) that this will be called on the subclass only once, thus data sources which allocate resources on creation do
     * not get duplicated many times.
     * 2) that this will only be called when access to the database is requested for the first time, meaning any
     * providers of data sources, such as the database connection manager, will already be initialised.
     */
    protected abstract fun bindDataSource(): DataSource

    /** Lazy to respect the guarantees given to subclasses, see [bindDataSource] */
    private val dataSource: DataSource by lazy { bindDataSource() }

    /**
     * Provides access to the db connection within this class, and manages the lifecycle of that connection such that
     * it is closed when finished. The dataSource is not intended to be accessed outside this function.
     */
    private inline fun <T> withDbConnection(block: (Connection) -> T): T {
        return dataSource.connection!!.use { connection -> block(connection) }
    }

    /**
     * Create "logical" DB (Schema) with [schemaName] for [user] and the given [privilege]. In case of DML privilege,
     * DDL user should be provided as [grantee].
     */
    fun createDbAndUser(
        schemaName: String,
        user: String,
        password: String,
        privilege: DbPrivilege,
        grantee: String?
    ) {
        // NOTE - This is currently Postgres specific and we will need to provide alternative implementations
        //  for other DBs. So we may need to wrap this in a factory.
        log.info("Creating $schemaName $privilege User: $user")
        val permissions = if (privilege == DbPrivilege.DML) {
            if (grantee != null) {
                "ALTER DEFAULT PRIVILEGES FOR ROLE $grantee IN SCHEMA $schemaName GRANT SELECT, UPDATE, INSERT, DELETE ON TABLES"
            } else {
                "ALTER DEFAULT PRIVILEGES IN SCHEMA $schemaName GRANT SELECT, UPDATE, INSERT, DELETE ON TABLES"
            }
        } else {
            "GRANT ALL ON SCHEMA $schemaName"
        }
        val sql = """
            CREATE SCHEMA IF NOT EXISTS $schemaName;
            CREATE USER $user WITH PASSWORD '$password';
            GRANT $user TO current_user;
            GRANT USAGE ON SCHEMA $schemaName to $user;
            $permissions TO $user;
            """.trimIndent()

        withDbConnection { connection ->
            connection.createStatement().execute(sql)
            connection.commit()
        }
    }

    /**
     * Delete DB schema
     *
     * @param schemaName Schema name
     */
    fun deleteSchema(schemaName: String) {
        // NOTE - This is currently Postgres specific and we will need to provide alternative implementations
        //  for other DBs. So we may need to wrap this in a factory.
        log.info("Deleting schema: $schemaName")
        val sql = "DROP SCHEMA $schemaName CASCADE;"
        withDbConnection { connection ->
            connection.createStatement().execute(sql)
            connection.commit()
        }
    }

    /**
     * Check whether user exists in DB
     *
     * @param user Username
     * @return true if user exists in Db, false otherwise
     */
    @Suppress("NestedBlockDepth")
    fun userExists(user: String): Boolean {
        // NOTE - This is currently Postgres specific and we will need to provide alternative implementations
        //  for other DBs. So we may need to wrap this in a factory.
        log.info("Checking whether user: $user exists")
        val sql = "SELECT EXISTS(SELECT * FROM pg_user WHERE USENAME = ?)"
        withDbConnection { connection ->
            connection.prepareStatement(sql).use { preparedStatement ->
                preparedStatement.setString(1, user)
                preparedStatement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getBoolean(1)
                    }
                    throw SQLException("Query for checking whether user: $user exists did not return any row")
                }
            }
        }
    }

    /**
     * Delete DB user
     *
     * @param user Username
     */
    fun deleteUser(user: String) {
        // NOTE - This is currently Postgres specific and we will need to provide alternative implementations
        //  for other DBs. So we may need to wrap this in a factory.
        log.info("Deleting user: $user")
        val sql = "DROP USER $user;"
        withDbConnection { connection ->
            connection.createStatement().execute(sql)
            connection.commit()
        }
    }

    /**
     * Configure JDBC URL to use given DB schema
     *
     * @param jdbcUrl JDBC URL
     * @param schemaName Schema name
     * @return JDBC URL configured to use given DB schema
     */
    fun createJdbcUrl(schemaName: String) = "$jdbcRootUrl?currentSchema=$schemaName"
}
