package net.corda.processors.db.internal

internal const val CONFIG_DB_DRIVER = "database.cluster.driver"
internal const val CONFIG_JDBC_URL = "database.cluster.jdbc.url"
internal const val CONFIG_DB_USER = "database.cluster.user"
internal const val CONFIG_DB_PASS = "database.cluster.pass"
internal const val CONFIG_INSTANCE_ID = "instanceId"
internal const val CONFIG_MAX_POOL_SIZE = "database.max.pool.size"

internal const val CONFIG_DB_DRIVER_DEFAULT = "org.postgresql.Driver"
internal const val CONFIG_JDBC_URL_DEFAULT = "jdbc:postgresql://cluster-db:5432/cordacluster"
internal const val CONFIG_MAX_POOL_SIZE_DEFAULT = 10

internal const val PERSISTENCE_UNIT_NAME = "cluster-config"