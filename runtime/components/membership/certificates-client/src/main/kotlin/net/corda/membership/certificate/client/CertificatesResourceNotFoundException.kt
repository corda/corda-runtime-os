package net.corda.membership.certificate.client

import net.corda.v5.base.exceptions.CordaRuntimeException

class CertificatesResourceNotFoundException(override val message: String) : CordaRuntimeException(message)
