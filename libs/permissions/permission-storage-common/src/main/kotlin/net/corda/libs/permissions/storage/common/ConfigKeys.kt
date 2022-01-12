package net.corda.libs.permissions.storage.common

object ConfigKeys {
    const val BOOTSTRAP_CONFIG = "corda.boot"

    // Similar to `net.corda.processors.db.internal.ConstantsKt.CONFIG_JDBC_URL`
    const val DB_CONFIG_KEY = "database.cluster"
    const val DB_URL = "jdbc.url"
    const val DB_USER = "user"
    const val DB_PASSWORD = "pass"
}