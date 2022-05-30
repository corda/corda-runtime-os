package net.corda.configuration.write.publish

import net.corda.data.config.ConfigurationSchemaVersion as ConfigurationSchemaVersionAvro

data class ConfigurationSchemaVersionDto(
    val majorVersion: Int,
    val minorVersion: Int
) {
    companion object {
        fun fromAvro(configurationSchemaVersionAvro: ConfigurationSchemaVersionAvro): ConfigurationSchemaVersionDto =
            ConfigurationSchemaVersionDto(
                configurationSchemaVersionAvro.majorVersion,
                configurationSchemaVersionAvro.minorVersion
            )
    }

    fun toAvro(): ConfigurationSchemaVersionAvro =
        ConfigurationSchemaVersionAvro(
            this.majorVersion,
            this.minorVersion
        )
}