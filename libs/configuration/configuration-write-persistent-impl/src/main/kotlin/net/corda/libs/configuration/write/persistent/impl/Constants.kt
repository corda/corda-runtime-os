package net.corda.libs.configuration.write.persistent.impl

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.messaging.api.subscription.RPCSubscription
import java.util.concurrent.CompletableFuture

internal typealias ConfigManagementResponseFuture = CompletableFuture<ConfigurationManagementResponse>
internal typealias ConfigManagementRPCSubscription = RPCSubscription<ConfigurationManagementRequest, ConfigurationManagementResponse>

internal const val DB_TABLE_CONFIG = "config"
internal const val GROUP_NAME = "config.management"
internal const val CLIENT_NAME_DB = "config.manager.db"
internal const val CLIENT_NAME_RPC = "config.manager.rpc"
internal const val PERSISTENCE_UNIT_NAME = "cluster-config"
internal const val MIGRATION_FILE_LOCATION = "migration/db.changelog-master.xml"
internal const val MAX_POOL_SIZE = 10

internal const val CONFIG_DB_DRIVER = "db.driver"
internal const val CONFIG_JDBC_URL = "db.jdbc.url"
internal const val CONFIG_DB_USER = "db.user"
internal const val CONFIG_DB_PASS = "db.pass"
internal const val CONFIG_DB_DRIVER_DEFAULT = "org.postgresql.Driver"
internal const val CONFIG_JDBC_URL_DEFAULT = "jdbc:postgresql://cluster-db:5432/cordacluster"
internal const val CONFIG_DB_USER_DEFAULT = "user"
internal const val CONFIG_DB_PASS_DEFAULT = "pass"