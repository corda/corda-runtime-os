package net.corda.serialization

interface InternalCustomSerializer<OBJ, PROXY>  {
    fun fromProxy(proxy: PROXY, context: SerializationContext): OBJ = fromProxy(proxy)

    fun fromProxy(proxy: PROXY): OBJ

    fun toProxy(obj: OBJ, context: SerializationContext): PROXY = toProxy(obj)

    fun toProxy(obj: OBJ): PROXY
}
