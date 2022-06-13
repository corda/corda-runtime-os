package net.corda.membership.certificate.service.impl

internal class NoSuchNode(tenantId: String) : CertificatesServiceException("No node named $tenantId")
