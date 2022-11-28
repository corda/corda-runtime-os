package net.corda.membership.certificate.client

import net.corda.v5.base.exceptions.CordaRuntimeException

class MissingClientCertificateInMutualTls : CordaRuntimeException("Mutual TLS must have client certificates")
