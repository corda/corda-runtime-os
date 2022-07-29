package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CpkMetadata
import java.nio.file.Path

interface CpkLoader {
    fun loadCPK(
        source: ByteArray,
        cacheDir: Path?,
        cpkLocation: String?,
        verifySignature: Boolean,
        cpkFileName: String?,
    ): Cpk

    fun loadMetadata(source: ByteArray, cpkLocation: String?, verifySignature: Boolean): CpkMetadata
}