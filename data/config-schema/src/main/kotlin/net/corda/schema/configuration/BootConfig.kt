package net.corda.schema.configuration

import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE

/**
 * Configuration paths for values used to bootstrap the worker
 */
object BootConfig {
    const val INSTANCE_ID = "instanceId"
    const val TOPIC_PREFIX = "topicPrefix"
    const val BOOT_MAX_ALLOWED_MSG_SIZE = MAX_ALLOWED_MSG_SIZE

    const val BOOT_KAFKA = "kafka"
    const val BOOT_KAFKA_COMMON = "$BOOT_KAFKA.common"
    const val BOOT_CRYPTO = "crypto"

    const val BOOT_DB = "db"
    const val BOOT_DB_PARAMS = "$BOOT_DB.params"
    const val BOOT_JDBC_URL = "$BOOT_DB_PARAMS.database.jdbc.url"
    const val BOOT_JDBC_USER = "$BOOT_DB_PARAMS.database.user"
    const val BOOT_JDBC_PASS = "$BOOT_DB_PARAMS.database.pass"

    const val BOOT_DIR = "dir"
    const val BOOT_WORKSPACE_DIR = "$BOOT_DIR.workspace"
    const val BOOT_TMP_DIR = "$BOOT_DIR.tmp"
}
