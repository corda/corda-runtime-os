package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.sql.SQLException

@Component(service = [DbAdmin::class])
class DbAdminImpl @Activate constructor(
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager
): DbAdmin {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createDbAndUser(
        schemaName: String,
        user: String,
        password: String,
        privilege: DbPrivilege,
        grantee: String?) {
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
        dbConnectionManager.getClusterDataSource().connection.use {
            it.createStatement().execute(sql)
            it.commit()
        }
    }

    override fun deleteSchema(schemaName: String) {
        // NOTE - This is currently Postgres specific and we will need to provide alternative implementations
        //  for other DBs. So we may need to wrap this in a factory.
        log.info("Deleting schema: $schemaName")
        val sql = "DROP SCHEMA $schemaName CASCADE;"
        dbConnectionManager.getClusterDataSource().connection.use {
            it.createStatement().execute(sql)
            it.commit()
        }
    }

    @Suppress("NestedBlockDepth")
    override fun userExists(user: String): Boolean {
        // NOTE - This is currently Postgres specific and we will need to provide alternative implementations
        //  for other DBs. So we may need to wrap this in a factory.
        log.info("Checking whether user: $user exists")
        val sql = "SELECT EXISTS(SELECT * FROM pg_user WHERE USENAME = ?)"
        dbConnectionManager.getClusterDataSource().connection.use { connection ->
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

    override fun deleteUser(user: String) {
        // NOTE - This is currently Postgres specific and we will need to provide alternative implementations
        //  for other DBs. So we may need to wrap this in a factory.
        log.info("Deleting user: $user")
        val sql = "DROP USER $user;"
        dbConnectionManager.getClusterDataSource().connection.use {
            it.createStatement().execute(sql)
            it.commit()
        }
    }

    override fun createJdbcUrl(jdbcUrl: String, schemaName: String) = "$jdbcUrl?currentSchema=$schemaName"
}