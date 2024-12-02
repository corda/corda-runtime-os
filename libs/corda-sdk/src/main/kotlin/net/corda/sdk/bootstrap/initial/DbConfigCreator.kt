package net.corda.sdk.bootstrap.initial

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.typesafe.config.ConfigRenderOptions
import net.corda.libs.configuration.secret.SecretsCreateService

/**
 * Generate a JSON config string for the config database.
 *
 * @param jdbcUrl URL for the database.
 * @param username Username for the database connection.
 * @param value
 * @param key Vault key for the secrets service. Used only by VAULT type secrets service.
 * @param jdbcPoolMaxSize The maximum size for the JDBC connection pool.
 * @param jdbcPoolMinSize The minimum size for the JDBC connection pool.
 * @param idleTimeout The maximum time (in seconds) a connection can stay idle in the pool.
 * @param maxLifetime The maximum time (in seconds) a connection can stay in the pool.
 * @param keepaliveTime The interval time (in seconds) in which connections will be tested for aliveness.
 * @param validationTimeout The maximum time (in seconds) that the pool will wait for a connection to be validated as alive.
 * @param secretsService A factory that can produce representations of secrets.
 * @return A string containing a JSON config.
 *
 */
@Suppress("LongParameterList")
fun createConfigDbConfig(
    jdbcUrl: String,
    username: String,
    value: String,
    key: String,
    jdbcPoolMaxSize: Int,
    jdbcPoolMinSize: Int?,
    idleTimeout: Int,
    maxLifetime: Int,
    keepaliveTime: Int,
    validationTimeout: Int,
    secretsService: SecretsCreateService,
): String {
    return "{\"database\":{" +
        "\"jdbc\":" +
        "{\"url\":${jacksonObjectMapper().writeValueAsString(jdbcUrl)}}," +
        "\"pass\":${createSecureConfig(secretsService, value, key)}," +
        "\"user\":${jacksonObjectMapper().writeValueAsString(username)}," +
        "\"pool\":" +
        "{\"max_size\":$jdbcPoolMaxSize," +
        if (jdbcPoolMinSize != null) { "\"min_size\":$jdbcPoolMinSize," } else { "" } +
        "\"idleTimeoutSeconds\":$idleTimeout," +
        "\"maxLifetimeSeconds\":$maxLifetime," +
        "\"keepaliveTimeSeconds\":$keepaliveTime," +
        "\"validationTimeoutSeconds\":$validationTimeout}}}"
}

fun createSecureConfig(secretsService: SecretsCreateService, value: String, key: String): String {
    return secretsService.createValue(value, key).root().render(ConfigRenderOptions.concise())
}
