package net.corda.libs.packaging.internal

import net.corda.libs.packaging.internal.v2.JarEntryAndBytes

interface ExternalChannelsConfigLoader {

    companion object {
        const val EXTERNAL_CHANNELS_CONFIG_FILE_NAME =
            "external-channels.json" // Todos: this probably should be in a more accessible file
    }

    /**
     * Loads an external channel configuration from a java entry which corresponds to a file
     */
    fun read(javaEntries: List<JarEntryAndBytes>): String?
}
