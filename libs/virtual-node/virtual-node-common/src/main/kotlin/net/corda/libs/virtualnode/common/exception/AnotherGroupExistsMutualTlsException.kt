package net.corda.libs.virtualnode.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Another group exists in the cluster in mutual TLS mode
 */
class AnotherGroupExistsMutualTlsException :
    CordaRuntimeException("Another group already exists in the cluster in mutual TLS.")
