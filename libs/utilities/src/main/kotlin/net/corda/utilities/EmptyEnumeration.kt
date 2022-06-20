package net.corda.utilities

import java.util.*

class EmptyEnumeration<E> : Enumeration<E> {
    override fun hasMoreElements(): Boolean = false

    override fun nextElement(): E {
        throw NoSuchElementException()
    }
}