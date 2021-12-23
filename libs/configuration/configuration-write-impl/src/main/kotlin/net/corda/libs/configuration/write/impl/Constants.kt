package net.corda.libs.configuration.write.impl

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.messaging.api.subscription.RPCSubscription
import java.util.concurrent.CompletableFuture

internal typealias ConfigurationManagementResponseFuture = CompletableFuture<ConfigurationManagementResponse>
internal typealias ConfigurationManagementRPCSubscription =
        RPCSubscription<ConfigurationManagementRequest, ConfigurationManagementResponse>

internal const val GROUP_NAME = "config.management"
internal const val CLIENT_NAME_DB = "config.manager.db"
internal const val CLIENT_NAME_RPC = "config.manager.rpc"

internal const val PERSISTENCE_UNIT_NAME = "cluster-config"
internal const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"
internal const val MAX_POOL_SIZE = 10
internal const val CONFIG_DB_DRIVER = "db.driver"
internal const val CONFIG_JDBC_URL = "db.jdbc.url"
internal const val CONFIG_DB_USER = "db.user"
internal const val CONFIG_DB_PASS = "db.pass"

internal const val DB_TABLE_CONFIG = "config"
internal const val DB_TABLE_CONFIG_AUDIT = "config_audit"
internal const val CONFIG_AUDIT_GENERATOR = "config_audit_generator"