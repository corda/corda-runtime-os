package net.corda.cpiinfo.read.fake

import net.corda.libs.packaging.CordappManifest
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.ManifestCordappInfo
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash

object TestCatalogue {
    object Cpi {
        fun createMetadata(cpiName: String, cpkName: String): CpiMetadata {
            return CpiMetadata(
                CpiIdentifier(cpiName, "0.0", SecureHash("ALG", byteArrayOf(0, 0, 0, 0))),
                SecureHash("ALG", byteArrayOf(0, 0, 0, 0)),
                listOf(
                    CpkMetadata(
                        CpkIdentifier(cpkName, "0.0", SecureHash("ALG", byteArrayOf(0, 0, 0, 0))),
                        Cpk.Manifest.newInstance(Cpk.FormatVersion.newInstance(0, 0)),
                        "",
                        listOf(),
                        listOf(),
                        CordappManifest(
                            "",
                            "",
                            0,
                            0,
                            ManifestCordappInfo(null, null, null, null),
                            ManifestCordappInfo(null, null, null, null),
                            mapOf()
                        ),
                        Cpk.Type.UNKNOWN,
                        SecureHash("ALG", byteArrayOf(0, 0, 0, 0)),
                        setOf()
                    )
                ),
                ""
            )
        }
    }
}