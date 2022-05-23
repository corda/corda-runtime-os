package net.corda.libs.packaging.core

import net.corda.data.packaging.ManifestCorDappInfo as ManifestCorDappInfoAvro

/** Information on a contract or workflow CorDapp in a [CordappManifest]. */
data class ManifestCorDappInfo(
        val shortName: String?,
        val vendor: String?,
        val versionId: Int?,
        val licence: String?) {

        companion object {
                fun fromAvro(other: ManifestCorDappInfoAvro): ManifestCorDappInfo = ManifestCorDappInfo(
                        other.shortName,
                        other.vendor,
                        other.versionId,
                        other.license)
        }

        fun toAvro(): ManifestCorDappInfoAvro =
                ManifestCorDappInfoAvro(shortName, vendor, versionId, licence)
}