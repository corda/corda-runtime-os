package net.corda.serialization

interface InternalCustomSerializer<OBJ, PROXY>  {
    val type: Class<OBJ>
    val proxyType: Class<PROXY>
    val withInheritance: Boolean

    fun fromProxy(proxy: PROXY, context: SerializationContext): OBJ = fromProxy(proxy)

    fun fromProxy(proxy: PROXY): OBJ

    fun toProxy(obj: OBJ, context: SerializationContext): PROXY = toProxy(obj)

    fun toProxy(obj: OBJ): PROXY
}
