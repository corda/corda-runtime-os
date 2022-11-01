package net.corda.membership.certificates

import net.corda.data.certificates.CertificateUsage

/**
 * Enum class to
 */
object CertificateUsageUtils {
    val CertificateUsage.publicName: String
        get() = name.lowercase().replace("_", "-")
}
