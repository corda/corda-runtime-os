package net.corda.libs.statemanager

class PrimitiveTypeCollection<E : Any>(
    private val collection: MutableCollection<E>
) : MutableCollection<E> by collection {

    override fun add(element: E): Boolean {
//        if (!(element::class.java.isPrimitive)) {
//            throw IllegalArgumentException("Primitives only please")
//        }

        return collection.add(element)
    }
}