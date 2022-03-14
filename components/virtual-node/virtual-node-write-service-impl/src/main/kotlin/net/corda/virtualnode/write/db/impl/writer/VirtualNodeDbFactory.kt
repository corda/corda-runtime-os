package net.corda.virtualnode.write.db.impl.writer

import com.typesafe.config.ConfigFactory
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbAdmin
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.createDbConfig
import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbType.CRYPTO
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbType.VAULT
import java.security.SecureRandom

/**
 * A factory for [VirtualNodeDb]s.
 */
class VirtualNodeDbFactory(
    private val dbConnectionManager: DbConnectionManager,
    private val dbAdmin: DbAdmin,
    private val schemaMigrator: LiquibaseSchemaMigrator
) {
    private val smartConfigFactory = dbConnectionManager.clusterConfig.factory
    private val adminJdbcUrl = dbConnectionManager.clusterConfig.getString(ConfigKeys.JDBC_URL)

    companion object {
        private const val ddlMaxPoolSize = 1
        private const val dmlMaxPoolSize = 1
        private const val passwordLength = 64
        private val passwordSource = (('0'..'9') + ('A'..'Z') + ('a'..'z')).toCharArray()
        private val random = SecureRandom()
    }

    /**
     * Creates [VirtualNodeDb]s using connection configurations from virtual node creation request
     *
     * @param holdingIdentityId Holding identity ID (short hash)
     * @param request Virtual node creation request
     *
     * @return map of [VirtualNodeDbType]s to [VirtualNodeDb]s
     */
    fun createVNodeDbs(holdingIdentityId: String, request: VirtualNodeCreationRequest): Map<VirtualNodeDbType, VirtualNodeDb> {
        with (request) {
            return mapOf(
                Pair(VAULT, createVNodeDb(VAULT, holdingIdentityId, vaultDdlConnection, vaultDmlConnection)),
                Pair(CRYPTO, createVNodeDb(CRYPTO, holdingIdentityId, cryptoDdlConnection, cryptoDmlConnection))
            )
        }
    }

    /**
     * Creates [VirtualNodeDb] with DDL and DML connections from provided connection configurations. If DML connection
     * configuration is not provided, cluster connections are created.
     *
     * @param dbType Virtual node database type
     * @param holdingIdentityId Holding identity ID (short hash)
     * @param ddlConfig DDL connection configuration
     * @param dmlConfig DML connection configuration
     */
    private fun createVNodeDb(dbType: VirtualNodeDbType, holdingIdentityId: String, ddlConfig: String?, dmlConfig: String?): VirtualNodeDb {
        val connectionsProvided = !dmlConfig.isNullOrBlank()
        val dbConnections =
            if (connectionsProvided) {
                mapOf(
                    Pair(DDL, ddlConfig?.let { createConnection(dbType, holdingIdentityId, DDL, ddlConfig) }),
                    Pair(DML, dmlConfig?.let { createConnection(dbType, holdingIdentityId, DML, dmlConfig) }))
            } else {
                mapOf(
                    Pair(DDL, createClusterConnection(dbType, holdingIdentityId, DDL)),
                    Pair(DML, createClusterConnection(dbType, holdingIdentityId, DML)))
            }
        return VirtualNodeDb(dbType, !connectionsProvided, holdingIdentityId, dbConnections, dbAdmin, dbConnectionManager, schemaMigrator)
    }

    /**
     * Creates [DbConnection] from provided connection configuration
     *
     * @param dbType Virtual node database type
     * @param holdingIdentityId Holding identity ID (short hash)
     * @param dbPrivilege Database privilege
     * @param config Connection configuration
     *
     * @return [DbConnection] created from provided connection configuration
     */
    private fun createConnection(
        dbType: VirtualNodeDbType, holdingIdentityId: String, dbPrivilege: DbPrivilege, config: String): DbConnection {
        with (dbType) {
            return DbConnection(
                getConnectionName(holdingIdentityId),
                dbPrivilege,
                config.toSmartConfig(),
                getConnectionDescription(dbPrivilege, holdingIdentityId)
            )
        }
    }

    /**
     * Creates cluster [DbConnection]
     *
     * @param dbType Virtual node database type
     * @param holdingIdentityId Holding identity ID (short hash)
     * @param dbPrivilege Database privilege
     *
     * @return created cluster [DbConnection]
     */
    private fun createClusterConnection(dbType: VirtualNodeDbType, holdingIdentityId: String, dbPrivilege: DbPrivilege): DbConnection {
        with (dbType) {
            val user = getUserName(dbPrivilege, holdingIdentityId)
            val password = generatePassword()
            val maxPoolSize = when(dbPrivilege) {
                DDL -> ddlMaxPoolSize
                DML -> dmlMaxPoolSize
            }
            // TODO support for CharArray passwords in SmartConfig
            val config = createDbConfig(
                smartConfigFactory, user, password.concatToString(),
                jdbcUrl = dbAdmin.createJdbcUrl(adminJdbcUrl, getSchemaName(holdingIdentityId)),
                maxPoolSize = maxPoolSize)
            return DbConnection(
                getConnectionName(holdingIdentityId),
                dbPrivilege,
                config,
                getConnectionDescription(dbPrivilege, holdingIdentityId)
            )
        }
    }

    /**
     * Generates random DB password
     */
    private fun generatePassword(): CharArray {
        val password = CharArray(passwordLength)
        for (i in 0 until passwordLength) password[i] = passwordSource[random.nextInt(passwordSource.size)]
        return password
    }

    /**
     * Creates a SmartConfig from configuration String
     *
     * @return SmartConfig created from configuration String
     */
    private fun String.toSmartConfig() = smartConfigFactory.create(ConfigFactory.parseString(this))
}