package net.corda.processors.db.internal.config

import net.corda.messaging.api.records.Record

internal const val CONFIG_TABLE_NAME = "config"
internal const val GROUP_NAME = "DB_EVENT_HANDLER" // TODO - Joel - Choose a proper group name.
internal const val CONFIG_UPDATE_REQUEST_TOPIC = "config-update-request"
internal const val CONFIG_TOPIC = "config"

internal typealias StringRecord = Record<String, String>