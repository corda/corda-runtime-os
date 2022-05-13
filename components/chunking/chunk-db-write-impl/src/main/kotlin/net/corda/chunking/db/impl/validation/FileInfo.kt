package net.corda.chunking.db.impl.validation

import net.corda.data.chunking.PropertyKeys
import net.corda.v5.crypto.SecureHash
import java.nio.file.Path

/** Simple class containing information about the file produced from combining [net.corda.data.chunking.Chunk] objects */
internal data class FileInfo(val name: String, val path: Path, val checksum: SecureHash, val properties: Map<String, String?>?) {
    val forceUpload: Boolean get() {
        return properties?.get(PropertyKeys.FORCE_UPLOAD)?.let { java.lang.Boolean.parseBoolean(it) } ?: false
    }
}
