package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpi
import java.io.InputStream
import java.nio.file.Path

interface CpiLoader {
    fun loadCpi(inputStream: InputStream, expansionLocation: Path, cpiLocation: String?, verifySignature: Boolean): Cpi
}