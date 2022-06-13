package net.corda.membership.certificates.datamodel

object CertificateEntities {
    val vnodeClasses = setOf(
        Certificate::class.java,
    )
    val clusterClasses = setOf(
        ClusterCertificate::class.java,
    )
}
