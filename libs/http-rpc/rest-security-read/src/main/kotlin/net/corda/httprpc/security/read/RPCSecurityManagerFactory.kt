package net.corda.httprpc.security.read

interface RPCSecurityManagerFactory {
    fun createRPCSecurityManager(): RestSecurityManager
}
