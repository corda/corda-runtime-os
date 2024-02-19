package net.corda.virtualnode.write.db.impl.writer

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.crypto.core.ShortHash
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.connection.manager.VirtualNodeDbType.CRYPTO
import net.corda.db.connection.manager.VirtualNodeDbType.UNIQUENESS
import net.corda.db.connection.manager.VirtualNodeDbType.VAULT
import net.corda.db.core.DbPrivilege
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.DatabaseConfig
import net.corda.schema.configuration.VirtualNodeDatasourceConfig
import net.corda.virtualnode.write.db.impl.VirtualNodesDbAdmin
import java.security.SecureRandom

/**
 * A factory for [VirtualNodeDb]s.
 */
internal class VirtualNodeDbFactoryImpl(
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodesDbAdmin: VirtualNodesDbAdmin,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val virtualNodesDdlPoolConfig: SmartConfig,
    private val virtualNodesDmlPoolConfig: SmartConfig
) : VirtualNodeDbFactory {
    private val smartConfigFactory = dbConnectionManager.clusterConfig.factory

    companion object {
        private const val passwordLength = 64
        private val passwordSource = (('0'..'9') + ('A'..'Z') + ('a'..'z')).toCharArray()
        private val random = SecureRandom()
    }

    /**
     * Creates [VirtualNodeDb]s using connection configurations from virtual node creation request
     *
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     * @param request Virtual node creation request
     *
     * @return map of [VirtualNodeDbType]s to [VirtualNodeDb]s
     */
    override fun createVNodeDbs(
        holdingIdentityShortHash: ShortHash,
        request: VirtualNodeConnectionStrings
    ): Map<VirtualNodeDbType, VirtualNodeDb> {
        with(request) {
            return mapOf(
                Pair(
                    VAULT,
                    createVNodeDb(VAULT, holdingIdentityShortHash, vaultDdlConnection, vaultDmlConnection)
                ),
                Pair(
                    CRYPTO,
                    createVNodeDb(CRYPTO, holdingIdentityShortHash, cryptoDdlConnection, cryptoDmlConnection)
                ),
                Pair(
                    UNIQUENESS,
                    createVNodeDb(
                        UNIQUENESS,
                        holdingIdentityShortHash,
                        uniquenessDdlConnection,
                        uniquenessDmlConnection
                    )
                )
            )
        }
    }

    /**
     * Creates [VirtualNodeDb] with DDL and DML connections from provided connection configurations. If DML connection
     * configuration is not provided, cluster connections are created.
     *
     * @param dbType Virtual node database type
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     * @param ddlConfig DDL connection configuration
     * @param dmlConfig DML connection configuration
     */
    private fun createVNodeDb(
        dbType: VirtualNodeDbType,
        holdingIdentityShortHash: ShortHash,
        ddlConfig: String?,
        dmlConfig: String?
    ): VirtualNodeDb {
        val usingClusterDb = dmlConfig.isNullOrBlank()
        val noUniquenessDb = dbType == UNIQUENESS && dmlConfig == "none"

        val dbConnections =
            if (usingClusterDb) {
                mapOf(
                    Pair(DDL, createClusterConnection(dbType, holdingIdentityShortHash, DDL)),
                    Pair(DML, createClusterConnection(dbType, holdingIdentityShortHash, DML))
                )
            } else if (noUniquenessDb) {
                mapOf(
                    Pair(DDL, null),
                    Pair(DML, null)
                )
            } else {
                mapOf(
                    Pair(DDL, ddlConfig?.let { createConnection(dbType, holdingIdentityShortHash, DDL, ddlConfig) }),
                    Pair(DML, dmlConfig?.let { createConnection(dbType, holdingIdentityShortHash, DML, dmlConfig) })
                )
            }

        val ddlProvided = ddlConfig?.isNotBlank() == true
        val hasConnections = dbConnections.values.any { it != null }
        val connectionStringsProvided = hasConnections && ddlProvided && !usingClusterDb

        return VirtualNodeDbImpl(
            usingClusterDb,
            connectionStringsProvided,
            dbConnections,
            dbType,
            holdingIdentityShortHash,
            virtualNodesDbAdmin,
            dbConnectionManager,
            schemaMigrator
        )
    }

    /**
     * Creates [DbConnectionImpl] from provided connection configuration
     *
     * @param dbType Virtual node database type
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     * @param dbPrivilege Database privilege
     * @param config Connection configuration
     *
     * @return [DbConnectionImpl] created from provided connection configuration
     */
    private fun createConnection(
        dbType: VirtualNodeDbType,
        holdingIdentityShortHash: ShortHash,
        dbPrivilege: DbPrivilege,
        config: String
    ): DbConnectionImpl {
        with(dbType) {
            val smartConfig = if (!config.toSmartConfig().hasPath("database.pool")) {
                val virtualNodePoolConfig = smartConfigFactory.create(
                    when (dbPrivilege) {
                        DDL -> virtualNodesDdlPoolConfig
                        DML -> virtualNodesDmlPoolConfig
                    }
                )
                createVirtualNodePoolConfig(config.toSmartConfig(), virtualNodePoolConfig)
            } else {
                config.toSmartConfig()
            }
            return DbConnectionImpl(
                getConnectionName(holdingIdentityShortHash),
                dbPrivilege,
                smartConfig,
                getConnectionDescription(dbPrivilege, holdingIdentityShortHash)
            )
        }
    }

    /**
     * Creates cluster [DbConnectionImpl]
     *
     * @param dbType Virtual node database type
     * @param holdingIdentityShortHash Holding identity ID (short hash)
     * @param dbPrivilege Database privilege
     *
     * @return created cluster [DbConnectionImpl]
     */
    private fun createClusterConnection(
        dbType: VirtualNodeDbType,
        holdingIdentityShortHash: ShortHash,
        dbPrivilege: DbPrivilege
    ): DbConnectionImpl {
        with(dbType) {
            val user = createUsername(dbPrivilege, holdingIdentityShortHash)
            val password = generatePassword()

            // Add reWriteBatchedInserts JDBC parameter for uniqueness db to enable Hibernate batching
            var jdbcUrl = virtualNodesDbAdmin.getJdbcUrl()
            if (dbType == UNIQUENESS && jdbcUrl.startsWith("jdbc:postgresql")) {
                jdbcUrl += "?reWriteBatchedInserts=true"
            }

            val virtualNodePoolConfig = smartConfigFactory.create(
                when (dbPrivilege) {
                    DDL -> virtualNodesDdlPoolConfig
                    DML -> virtualNodesDmlPoolConfig
                }
            )

            // TODO support for CharArray passwords in SmartConfig
            val config = createVirtualNodeDbConfig(
                smartConfigFactory,
                username = user,
                password = password.concatToString(),
                key = "corda-vault-$holdingIdentityShortHash-database-password",
                jdbcDriver = null,
                jdbcUrl = jdbcUrl,
                virtualNodePoolConfig = virtualNodePoolConfig
            )
            return DbConnectionImpl(
                getConnectionName(holdingIdentityShortHash),
                dbPrivilege,
                config,
                getConnectionDescription(dbPrivilege, holdingIdentityShortHash)
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

@Suppress("LongParameterList")
private fun createVirtualNodeDbConfig(
    smartConfigFactory: SmartConfigFactory,
    username: String,
    password: String,
    key: String,
    jdbcDriver: String?,
    jdbcUrl: String,
    virtualNodePoolConfig: SmartConfig
): SmartConfig {
    var config =
        smartConfigFactory.makeSecret(password, key).atPath(DatabaseConfig.DB_PASS)
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(username))

    if (jdbcDriver != null) {
        config = config.withValue(DatabaseConfig.JDBC_DRIVER, ConfigValueFactory.fromAnyRef(jdbcDriver))
    }
    config = config.withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcUrl))

    config = createVirtualNodePoolConfig(config, virtualNodePoolConfig)

    return config
}

private fun createVirtualNodePoolConfig(
    config: SmartConfig,
    virtualNodePoolConfig: SmartConfig
): SmartConfig {
    val maxPoolSize = virtualNodePoolConfig.getInt(VirtualNodeDatasourceConfig.VNODE_POOL_MAX_SIZE)
    var configWithPool = config.withValue(DatabaseConfig.DB_POOL_MAX_SIZE, ConfigValueFactory.fromAnyRef(maxPoolSize))

    if (virtualNodePoolConfig.hasPath(VirtualNodeDatasourceConfig.VNODE_POOL_MIN_SIZE)) {
        val minPoolSize = virtualNodePoolConfig.getInt(VirtualNodeDatasourceConfig.VNODE_POOL_MIN_SIZE)
        configWithPool = configWithPool.withValue(
            DatabaseConfig.DB_POOL_MIN_SIZE,
            ConfigValueFactory.fromAnyRef(minPoolSize)
        )
    }

    val idleTimeout = virtualNodePoolConfig.getInt(VirtualNodeDatasourceConfig.VNODE_POOL_IDLE_TIMEOUT_SECONDS)
    configWithPool = configWithPool.withValue(
        DatabaseConfig.DB_POOL_IDLE_TIMEOUT_SECONDS,
        ConfigValueFactory.fromAnyRef(idleTimeout)
    )
    val maxLifetime = virtualNodePoolConfig.getInt(VirtualNodeDatasourceConfig.VNODE_POOL_MAX_LIFETIME_SECONDS)
    configWithPool = configWithPool.withValue(
        DatabaseConfig.DB_POOL_MAX_LIFETIME_SECONDS,
        ConfigValueFactory.fromAnyRef(maxLifetime)
    )
    val keepaliveTime = virtualNodePoolConfig.getInt(VirtualNodeDatasourceConfig.VNODE_POOL_KEEPALIVE_TIME_SECONDS)
    configWithPool = configWithPool.withValue(
        DatabaseConfig.DB_POOL_KEEPALIVE_TIME_SECONDS,
        ConfigValueFactory.fromAnyRef(keepaliveTime)
    )
    val validationTimeout = virtualNodePoolConfig.getInt(VirtualNodeDatasourceConfig.VNODE_VALIDATION_TIMEOUT_SECONDS)
    configWithPool = configWithPool.withValue(
        DatabaseConfig.DB_POOL_VALIDATION_TIMEOUT_SECONDS,
        ConfigValueFactory.fromAnyRef(validationTimeout)
    )

    return configWithPool
}
