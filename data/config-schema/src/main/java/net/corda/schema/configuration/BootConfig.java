package net.corda.schema.configuration;

import static net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE;

/**
 * Configuration paths for values used to bootstrap the worker
 */
public final class BootConfig {
    private BootConfig() {
    }

    public static final String INSTANCE_ID = "instanceId";
    public static final String TOPIC_PREFIX = "topicPrefix";
    public static final String BOOT_MAX_ALLOWED_MSG_SIZE = MAX_ALLOWED_MSG_SIZE;

    public static final String BOOT_KAFKA = "kafka";
    public static final String BOOT_KAFKA_COMMON = BOOT_KAFKA + ".common";
    public static final String BOOT_CRYPTO = "crypto";

    public static final String BOOT_DB = "db";
    public static final String BOOT_DB_PARAMS = BOOT_DB + ".params";
    public static final String BOOT_JDBC_URL = BOOT_DB_PARAMS + ".database.jdbc.url";
    public static final String BOOT_JDBC_USER = BOOT_DB_PARAMS + ".database.user";
    public static final String BOOT_JDBC_PASS = BOOT_DB_PARAMS + ".database.pass";

    public static final String BOOT_DIR = "dir";
    public static final String BOOT_WORKSPACE_DIR = BOOT_DIR + ".workspace";
    public static final String BOOT_TMP_DIR = BOOT_DIR + ".tmp";
}
