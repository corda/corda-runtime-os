package net.corda.libs.configuration.dto

import net.corda.data.config.Configuration as ConfigurationAvro

data class ConfigurationDto(
    val section: String,
    val value: String,
    val version: Int,
    val configurationSchemaVersionDto: ConfigurationSchemaVersionDto
) {
    // TODO Maybe add section to ConfigurationAvro?
    companion object {
        fun fromAvro(section: String, configurationAvro: ConfigurationAvro): ConfigurationDto =
            ConfigurationDto(
                section,
                configurationAvro.value,
                configurationAvro.version.toInt(),
                ConfigurationSchemaVersionDto.fromAvro(configurationAvro.schemaVersion)
            )
    }

    fun toAvro(): Pair<String, ConfigurationAvro> =
        this.section to ConfigurationAvro(
            this.value,
            this.version.toString(),
            this.configurationSchemaVersionDto.toAvro()
        )
}