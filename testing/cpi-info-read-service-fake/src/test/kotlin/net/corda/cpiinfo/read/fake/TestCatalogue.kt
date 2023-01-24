package net.corda.cpiinfo.read.fake

import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.v5.crypto.SecureHash
import java.time.Instant

object TestCatalogue {
    object Cpi {
        fun createMetadata(cpiName: String, cpkName: String): CpiMetadata {
            val timestamp = Instant.now()
            return CpiMetadata(
                CpiIdentifier(cpiName, "0.0", SecureHash("ALG", byteArrayOf(0, 0, 0, 0))),
                SecureHash("ALG", byteArrayOf(0, 0, 0, 0)),
                listOf(
                    CpkMetadata(
                        CpkIdentifier(cpkName, "0.0", SecureHash("ALG", byteArrayOf(0, 0, 0, 0))),
                        CpkManifest(CpkFormatVersion(0, 0)),
                        "",
                        listOf(),
                        CordappManifest(
                            "",
                            "",
                            0,
                            0,
                            CordappType.WORKFLOW,
                            "",
                            "",
                            0,
                            "",
                            mapOf()
                        ),
                        CpkType.UNKNOWN,
                        SecureHash("ALG", byteArrayOf(0, 0, 0, 0)),
                        setOf(),
                        timestamp
                    )
                ),
                "",
                version = -1,
                timestamp
            )
        }
    }
}