package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer
import javax.security.auth.x500.X500Principal

class X500PrincipalSerializer : BaseProxySerializer<X500Principal, X500PrincipalSerializer.X500PrincipalProxy>() {
    override val type: Class<X500Principal> get() = X500Principal::class.java
    override val proxyType: Class<X500PrincipalProxy> get() = X500PrincipalProxy::class.java
    override val withInheritance: Boolean get() = false

    override fun toProxy(obj: X500Principal): X500PrincipalProxy = X500PrincipalProxy(name = obj.name)

    override fun fromProxy(proxy: X500PrincipalProxy): X500Principal = X500Principal(proxy.name)

    data class X500PrincipalProxy(val name: String)
}
