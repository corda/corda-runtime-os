package net.corda.processors.db.internal.config.writer

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse

internal const val CONFIG_TABLE_NAME = "config"
internal const val GROUP_NAME = "DB_EVENT_HANDLER" // TODO - Joel - Choose a proper group name.
internal const val CLIENT_NAME = "DB_CLIENT_NAME" // TODO - Joel - Choose a proper client name.
internal const val CLIENT_ID = "joel" // TODO - Joel - Choose a proper client ID. Should this be combined with above?
internal const val CONFIG_UPDATE_REQUEST_TOPIC = "config-update-request"
internal const val CONFIG_TOPIC = "config"
// TODO - Joel - Define own Avro objects, instead of reusing these existing ones.
internal val REQ_CLASS = PermissionManagementRequest::class.java
internal val RESP_CLASS = PermissionManagementResponse::class.java