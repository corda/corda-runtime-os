package net.corda.libs.packaging.internal

import net.corda.libs.packaging.internal.v2.JarEntryAndBytes

class ExternalChannelsConfigLoaderImpl : ExternalChannelsConfigLoader {
    override fun read(javaEntries: List<JarEntryAndBytes>): String? {
        val externalChannelsConfigLoader =
            javaEntries.filter {
                it.entry.name.equals(ExternalChannelsConfigLoader.EXTERNAL_CHANNELS_CONFIG_FILE_PATH)
            }
                .map { String(it.bytes) }

        return externalChannelsConfigLoader.singleOrNull()
    }
}
