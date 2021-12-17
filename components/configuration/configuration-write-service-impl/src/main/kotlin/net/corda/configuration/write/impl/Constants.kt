package net.corda.configuration.write.impl

internal const val MAX_POOL_SIZE = 1 // We choose a small pool as we only use it to check database connectivity.
internal const val CONFIG_DB_DRIVER = "db.driver"
internal const val CONFIG_JDBC_URL = "db.jdbc.url"
internal const val CONFIG_DB_USER = "db.user"
internal const val CONFIG_DB_PASS = "db.pass"