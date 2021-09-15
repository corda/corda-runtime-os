package net.corda.httprpc.security.read

import java.util.function.Supplier

interface RPCSecurityManagerFactory : Supplier<RPCSecurityManager>