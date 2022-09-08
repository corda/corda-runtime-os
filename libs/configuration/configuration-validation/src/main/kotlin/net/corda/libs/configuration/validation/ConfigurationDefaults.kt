package net.corda.libs.configuration.validation

import net.corda.libs.configuration.validation.impl.ConfigurationValidatorFactoryImpl
import net.corda.v5.base.versioning.Version

object ConfigurationDefaults {
    /** Version of DB configuration schema that will provide default values */
    val DB_SCHEMA_VER = Version(1, 0)
}

/**
 * Retrieves default values set in configuration schema
 *
 * @param key The top-level configuration key
 * @param version The schema version
 * @return default values set in configuration schema
 */
fun getConfigurationDefaults(key: String, version: Version) =
    ConfigurationValidatorFactoryImpl()
        .createConfigValidator()
        .getDefaults(key, version)