package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpi
import java.nio.file.Path

interface CpiLoader {
    fun loadCpi(byteArray: ByteArray, expansionLocation: Path, cpiLocation: String?, verifySignature: Boolean): Cpi
}