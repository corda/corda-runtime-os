package net.corda.libs.packaging.internal

import java.lang.IllegalArgumentException
import net.corda.libs.packaging.PackagingConstants
import net.corda.libs.packaging.internal.v2.JarEntryAndBytes

class ExternalChannelsConfigLoaderImpl : ExternalChannelsConfigLoader {
    override fun read(javaEntries: List<JarEntryAndBytes>): String? {
        val externalChannelsConfigLoader =
            javaEntries.filter {
                it.entry.name.equals(
                    "${PackagingConstants.CPK_CONFIG_FOLDER}/${ExternalChannelsConfigLoader.EXTERNAL_CHANNELS_CONFIG_FILE_NAME}"
                )
            }
                .map { String(it.bytes) }

        require(externalChannelsConfigLoader.size <= 1) {
            throw IllegalArgumentException("More than one configuration file was found for the external channels")
        }

        return externalChannelsConfigLoader.singleOrNull()
    }
}
