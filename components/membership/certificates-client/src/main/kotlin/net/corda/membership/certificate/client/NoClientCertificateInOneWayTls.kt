package net.corda.membership.certificate.client

import net.corda.v5.base.exceptions.CordaRuntimeException

class NoClientCertificateInOneWayTls : CordaRuntimeException("One way TLS can not support client certificates")
