package net.corda.membership.certificate.service.impl

internal class UnknownTypeException(type: Any?) : CertificatesServiceException("Unknown certificate type: $type")
