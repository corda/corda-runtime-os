package net.corda.cpk.write

import net.corda.lifecycle.Lifecycle
import net.corda.packaging.CPK
import java.io.InputStream

interface CpkWriteService : Lifecycle {
    /** put the cpk into some implementation specific backing storage */
    fun put(cpkMetadata: CPK.Metadata, inputStream: InputStream)

    /** remove the cpk from some implementation specific backing storage */
    fun remove(cpkMetadata: CPK.Metadata)
}
