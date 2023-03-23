package net.corda.cli.plugin.secretconfig

import com.typesafe.config.Config

interface SecretConfigGenerator {
    /**
     * Generate the secret Config.
     *
     * @param value The value pertaining to this secret. This might be a secret in plain text, or might be a key referencing
     * a secret. The exact nature depends on the specific secret config generator implementation.
     * @return The Config snippet generated
     */
    fun generate(value: String): Config
}
