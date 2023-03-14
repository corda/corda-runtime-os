package net.corda.libs.packaging.internal

import net.corda.libs.packaging.PackagingConstants
import net.corda.libs.packaging.internal.v2.JarEntryAndBytes

interface ExternalChannelsConfigLoader {

    companion object {
        // Todos: this probably should be in a more accessible file
        const val EXTERNAL_CHANNELS_CONFIG_FILE_PATH =
            "${PackagingConstants.CPK_CONFIG_FOLDER}/external-channels.json"
    }

    /**
     * Loads an external channel configuration from a java entry which corresponds to a file
     */
    fun read(javaEntries: List<JarEntryAndBytes>): String?
}
