package net.corda.libs.packaging.core

import net.corda.data.packaging.CpkManifest as CpkManifestAvro

data class CpkManifest(val cpkFormatVersion: CpkFormatVersion) {
    companion object {
        fun fromAvro(other: CpkManifestAvro) : CpkManifest =
            CpkManifest(CpkFormatVersion.fromAvro(other.version))
    }
    fun toAvro(): CpkManifestAvro = CpkManifestAvro(cpkFormatVersion.toAvro())
}