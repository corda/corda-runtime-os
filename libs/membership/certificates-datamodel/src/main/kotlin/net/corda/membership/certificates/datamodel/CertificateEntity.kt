package net.corda.membership.certificates.datamodel

interface CertificateEntity {
    val usage: String
    val alias: String
    val rawCertificate: String
}
