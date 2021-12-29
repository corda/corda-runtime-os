package net.corda.processors.db.internal

internal const val CONFIG_DB_DRIVER = "db.driver"
internal const val CONFIG_JDBC_URL = "db.jdbc.url"
internal const val CONFIG_DB_USER = "db.user"
internal const val CONFIG_DB_PASS = "db.pass"
internal const val CONFIG_INSTANCE_ID = "instanceId"

internal const val CONFIG_DB_DRIVER_DEFAULT = "org.postgresql.Driver"
internal const val CONFIG_JDBC_URL_DEFAULT = "jdbc:postgresql://cluster-db:5432/cordacluster"
internal const val CONFIG_DB_USER_DEFAULT = "user"
internal const val CONFIG_DB_PASS_DEFAULT = "pass"

internal const val PERSISTENCE_UNIT_NAME = "cluster-config"
internal const val MAX_POOL_SIZE = 10
internal const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"