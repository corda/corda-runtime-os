package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import javax.security.auth.x500.X500Principal

class X500PrincipalSerializer : SerializationCustomSerializer<X500Principal, X500PrincipalSerializer.X500PrincipalProxy> {
    override fun toProxy(obj: X500Principal): X500PrincipalProxy = X500PrincipalProxy(name = obj.name)

    override fun fromProxy(proxy: X500PrincipalProxy): X500Principal = X500Principal(proxy.name)

    data class X500PrincipalProxy(val name: String)
}