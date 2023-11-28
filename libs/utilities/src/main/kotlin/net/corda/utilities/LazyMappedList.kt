package net.corda.utilities

import java.util.AbstractList

/**
 * List implementation that applies the expensive [transform] function only when the element is accessed and caches calculated values.
 * Size is very cheap as it doesn't call [transform].
 * Used internally by [net.corda.core.transactions.TraversableTransaction].
 */
class LazyMappedList<T, U>(val originalList: List<T>, val transform: (T, Int) -> U) : AbstractList<U>() {
    private val partialResolvedList = MutableList<U?>(originalList.size) { null }
    override val size get() = originalList.size
    override fun get(index: Int): U {
        return partialResolvedList[index]
            ?: transform(originalList[index], index).also { computed -> partialResolvedList[index] = computed }
    }

    fun eager(onError: (RuntimeException, Int) -> U?) {
        for (i in 0 until size) {
            try {
                get(i)
            } catch (ex: RuntimeException) {
                partialResolvedList[i] = onError(ex, i)
            }
        }
    }
}
