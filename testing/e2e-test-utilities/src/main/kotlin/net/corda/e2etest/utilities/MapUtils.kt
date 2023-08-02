package net.corda.e2etest.utilities

fun Map<*, *>.flatten(
    target: MutableMap<String, Any?> = mutableMapOf(),
    prefix: String? = null
): Map<String, Any?> {
    forEach { (k, v) ->
        val newPrefix = "${prefix?.let{"$it.$k"} ?: k}"
        if (v is Map<*, *>) {
            v.flatten(target, newPrefix)
        } else {
            target[newPrefix] = v
        }
    }
    return target
}

fun Map<String, Any?>.expand(): Map<String, Any?> {
    return mutableMapOf<String, Any?>().also { output ->
        forEach { (k, v) ->
            var targetMap: MutableMap<String, Any?> = output
            val splitKey = k.split('.')
            splitKey.dropLast(1).forEach {
                if (targetMap.contains(it)) {
                    @Suppress("unchecked_cast")
                    targetMap = targetMap[it] as MutableMap<String, Any?>
                } else {
                    val newMap = mutableMapOf<String, Any?>()
                    targetMap[it] = newMap
                    targetMap = newMap
                }
            }
            targetMap[splitKey.last()] = v
        }
    }
}