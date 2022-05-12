package net.corda.packaging

/** Information on a contract or workflow CorDapp in a [CordappManifest]. */
data class ManifestCordappInfo(
        val shortName: String?,
        val vendor: String?,
        val versionId: Int?,
        val licence: String?)