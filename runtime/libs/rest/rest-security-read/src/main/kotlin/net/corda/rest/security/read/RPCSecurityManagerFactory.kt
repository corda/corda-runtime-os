package net.corda.rest.security.read

interface RPCSecurityManagerFactory {
    fun createRPCSecurityManager(): RestSecurityManager
}
