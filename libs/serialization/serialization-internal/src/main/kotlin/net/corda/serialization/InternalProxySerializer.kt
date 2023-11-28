package net.corda.serialization

interface InternalProxySerializer<OBJ : Any, PROXY : Any> : InternalCustomSerializer<OBJ> {
    val proxyType: Class<PROXY>

    fun fromProxy(proxy: PROXY, context: SerializationContext): OBJ
    fun toProxy(obj: OBJ, context: SerializationContext): PROXY
}
