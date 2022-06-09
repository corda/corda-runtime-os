package net.corda.libs.packaging

import net.corda.libs.packaging.core.CpkMetadata
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path

/** Represents a Cpk file in the filesystem */
interface Cpk : AutoCloseable {

    /**
     * Stores the metadata associated with this Cpk file
     */
    val metadata : CpkMetadata

    /**
     * Path to cpk if it has been extracted or unpacked.  The filename
     * part of the path should not be depended on and probably won't be the
     * original name of the cpk.
     */
    val path : Path? get() = null

    /** File name of cpk, if known */
    val originalFileName : String? get() = null

    /**
     * Returns an [InputStream] with the content of the associated resource inside the Cpk archive
     * with the provided [resourceName] or null if a resource with that name doesn't exist.
     *
     * @param resourceName the name of the Cpk resource to be opened
     * @return an [InputStream] reading from the named resource
     * @throws [IOException] if a resource with the provided name is not found
     */
    @Throws(IOException::class)
    fun getResourceAsStream(resourceName : String) : InputStream
}

