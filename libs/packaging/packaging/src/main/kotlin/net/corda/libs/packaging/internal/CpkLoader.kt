package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CpkMetadata
import java.io.InputStream
import java.nio.file.Path

interface CpkLoader {
    fun loadCPK(
        source: InputStream,
        cacheDir: Path?,
        cpkLocation: String?,
        verifySignature: Boolean,
        cpkFileName: String?,
    ): Cpk

    fun loadMetadata(source: InputStream, cpkLocation: String?, verifySignature: Boolean): CpkMetadata
}