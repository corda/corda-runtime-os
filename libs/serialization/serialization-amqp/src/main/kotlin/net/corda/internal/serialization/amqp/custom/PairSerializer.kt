package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer

class PairSerializer : BaseProxySerializer<Pair<*, *>, PairSerializer.PairProxy>() {
    override fun toProxy(obj: Pair<*, *>): PairProxy {
        return PairProxy(obj.first, obj.second)
    }

    override fun fromProxy(proxy: PairProxy): Pair<*, *> {
        return Pair(proxy.a, proxy.b)
    }

    override val type: Class<Pair<*, *>>
        get() = Pair::class.java
    override val withInheritance: Boolean
        get() = false
    override val proxyType: Class<PairProxy>
        get() = PairProxy::class.java
        
    data class PairProxy(val a:Any?, val b: Any?)
}