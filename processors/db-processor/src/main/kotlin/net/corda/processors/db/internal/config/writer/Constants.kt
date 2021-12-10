package net.corda.processors.db.internal.config.writer

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse

internal const val DB_TABLE_CONFIG = "config"
// TODO - Joel - Choose a proper group name.
internal const val GROUP_NAME = "DB_EVENT_HANDLER"
// TODO - Joel - Choose proper DB and RPC client names.
internal const val CLIENT_NAME_DB = "DB_CLIENT_NAME"
internal const val CLIENT_NAME_RPC = "RPC_CLIENT_NAME"
internal const val TOPIC_CONFIG_UPDATE_REQUEST = "config-update-request"
internal const val TOPIC_CONFIG = "config"
// TODO - Joel - Define own Avro objects, instead of reusing these existing ones.
internal val REQ_CLASS = PermissionManagementRequest::class.java
internal val RESP_CLASS = PermissionManagementResponse::class.java
internal const val CONFIG_DB_DRIVER = "db.driver"
internal const val CONFIG_JDBC_URL = "db.jdbc.url"
internal const val CONFIG_DB_USER = "db.user"
internal const val CONFIG_DB_PASS = "db.pass"
internal const val PERSISTENCE_UNIT_NAME = "joel" // TODO - Joel - Choose better persistence unit name.
internal const val MIGRATION_FILE_LOCATION = "migration/db.changelog-master.xml"
internal const val MAX_POOL_SIZE = 10