package net.corda.membership.certificates

import net.corda.data.certificates.CertificateUsage

/**
 * Utility object to handle the [CertificateUsage] enum.
 */
object CertificateUsageUtils {
    /**
     * Extract the public name from a [CertificateUsage] value.
     */
    val CertificateUsage.publicName: String
        get() = name.lowercase().replace("_", "-")
}
