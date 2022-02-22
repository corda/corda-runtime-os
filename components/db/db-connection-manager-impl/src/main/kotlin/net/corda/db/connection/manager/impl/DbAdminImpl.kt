package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.db.connection.manager.createDbConfig
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [DbAdmin::class])
class DbAdminImpl @Activate constructor(
    @Reference(service = DbConnectionsRepository::class)
    private val dbConnectionsRepository: DbConnectionsRepository,
): DbAdmin {

    companion object {
        private val log = contextLogger()
    }

    // TODO Remove method below
    override fun createDbAndUser(
        persistenceUnitName: String,
        schemaName: String,
        user: String,
        password: String,
        jdbcUrl: String,
        privilege: DbPrivilege,
        configFactory: SmartConfigFactory) {
        // NOTE - This is currently Postgres specific and we will need to provide alternative implementations
        //  for other DBs. So we may need to wrap this in a factory.
        log.info("Creating $schemaName $privilege User: $user")
        val permissions = if (privilege == DbPrivilege.DML) {
            "ALTER DEFAULT PRIVILEGES IN SCHEMA $schemaName GRANT SELECT, UPDATE, INSERT, DELETE ON TABLES"
        } else {
            "GRANT ALL ON SCHEMA $schemaName"
        }
        val sql = """
            CREATE SCHEMA IF NOT EXISTS $schemaName;
            CREATE USER $user WITH PASSWORD '$password';
            GRANT USAGE ON SCHEMA $schemaName to $user;
            $permissions TO $user;
            """.trimIndent()
        dbConnectionsRepository.clusterDataSource.connection.use {
            it.createStatement().execute(sql)
            it.commit()
        }

        log.info("Persisting DB Configuration for $persistenceUnitName")
        dbConnectionsRepository.put(
            persistenceUnitName,
            privilege,
            createDbConfig(
                configFactory,
                user,
                password,
                jdbcUrl = "$jdbcUrl?currentSchema=$schemaName"),
            "$persistenceUnitName $privilege Connection - schema: $schemaName",
            "DbAdmin"
        )
    }

    override fun createDbAndUser(
        schemaName: String,
        user: String,
        password: String,
        jdbcUrl: String,
        privilege: DbPrivilege) {
        // NOTE - This is currently Postgres specific and we will need to provide alternative implementations
        //  for other DBs. So we may need to wrap this in a factory.
        log.info("Creating $schemaName $privilege User: $user")
        val permissions = if (privilege == DbPrivilege.DML) {
            "ALTER DEFAULT PRIVILEGES IN SCHEMA $schemaName GRANT SELECT, UPDATE, INSERT, DELETE ON TABLES"
        } else {
            "GRANT ALL ON SCHEMA $schemaName"
        }
        val sql = """
            CREATE SCHEMA IF NOT EXISTS $schemaName;
            DO
            $$
            BEGIN
              IF NOT EXISTS (SELECT * FROM pg_user WHERE USENAME = '$user') THEN 
                 CREATE USER $user WITH PASSWORD '$password';
              ELSE
                 ALTER USER $user WITH PASSWORD '$password';
              END IF;
            END
            $$;
            
            GRANT USAGE ON SCHEMA $schemaName to $user;
            $permissions TO $user;
            """.trimIndent()
        dbConnectionsRepository.clusterDataSource.connection.use {
            it.createStatement().execute(sql)
            it.commit()
        }
    }

    override fun createJdbcUrl(jdbcUrl: String, schemaName: String) = "$jdbcUrl?currentSchema=$schemaName"
}