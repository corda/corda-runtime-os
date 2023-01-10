package net.corda.testing.sandboxes

import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import net.corda.v5.base.types.MemberX500Name
import org.osgi.framework.BundleContext

interface SandboxSetup {
    companion object {
        const val SANDBOX_SERVICE_RANKING = Int.MAX_VALUE / 2
        const val CORDA_MEMBERSHIP_PID = "net.corda.testing.sandbox.membership"
        const val CORDA_MEMBER_COUNT = "net.corda.testing.sandbox.member.count"
        const val CORDA_MEMBER_X500_NAME = "net.corda.testing.sandbox.member.X500"
        const val CORDA_MEMBER_PUBLIC_KEY = "net.corda.testing.sandbox.member.PublicKey"
        const val CORDA_MEMBER_PRIVATE_KEY = "net.corda.testing.sandbox.member.PrivateKey"
        const val CORDA_LOCAL_IDENTITY_PID = "net.corda.testing.sandboxes.LocalIdentity"
        const val CORDA_TENANT_COUNT = "net.corda.testing.sandboxes.tenant.count"
        const val CORDA_TENANT = "net.corda.testing.sandboxes.tenant"
        const val CORDA_TENANT_MEMBER = "net.corda.testing.sandboxes.tenant.member"
        const val CORDA_LOCAL_TENANCY_PID = "net.corda.testing.sandboxes.LocalTenancy"
    }

    fun configure(bundleContext: BundleContext, baseDirectory: Path)

    fun start()
    fun shutdown()

    fun <T> getService(serviceType: Class<T>, filter: String?, timeout: Long): T
    fun <T> getService(serviceType: Class<T>, timeout: Long): T = getService(serviceType, null, timeout)

    fun setMembershipGroup(network: Map<MemberX500Name, PublicKey>)
    fun setLocalIdentities(localMembers: Set<MemberX500Name>, localKeys: Map<MemberX500Name, KeyPair>)
    fun configureLocalTenants(timeout: Long)

    fun withCleanup(closeable: AutoCloseable)
}

inline fun <reified T> SandboxSetup.fetchService(timeout: Long): T {
    return getService(T::class.java, timeout)
}

inline fun <reified T> SandboxSetup.fetchService(filter: String?, timeout: Long): T {
    return getService(T::class.java, filter, timeout)
}
