package net.corda.chunking.db.impl.validation

import net.corda.data.chunking.PropertyKeys
import net.corda.v5.crypto.SecureHash
import java.nio.file.Path

/**
 * Simple class containing information about the file produced from combining [net.corda.data.chunking.Chunk] objects
 *
 * @param name the original file name
 * @param path the path to the file.  NB. the _file name_ may not match the [name] parameter
 * @param checksum the checksum of the file
 * @param properties a bag of miscellaneous properties
 * */
data class FileInfo(val name: String, val path: Path, val checksum: SecureHash, val properties: Map<String, String?>?) {
    val forceUpload: Boolean get() {
        return properties?.get(PropertyKeys.FORCE_UPLOAD)?.let { java.lang.Boolean.parseBoolean(it) } ?: false
    }
    val resetDb: Boolean get() {
        return properties?.get(PropertyKeys.RESET_DB)?.let { java.lang.Boolean.parseBoolean(it) } ?: false
    }
}
