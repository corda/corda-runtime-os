package net.corda.libs.statemanager

class PrimitiveTypeMap<K, V : Any>(
    private val map: MutableMap<K, V>
) : MutableMap<K, V> by map {

    override fun put(key: K, value: V): V? {
//        if (!(value::class.java.isPrimitive)) {
//            throw IllegalArgumentException("Primitives only please")
//        }
        return map.put(key, value)
    }
}



