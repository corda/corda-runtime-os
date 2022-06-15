package net.corda.libs.packaging.verify.internal

import net.corda.libs.packaging.core.exception.CordappManifestException
import java.util.jar.Manifest

fun Manifest.requireAttribute(name: String) {
    if (mainAttributes.getValue(name) == null)
        throw CordappManifestException("Manifest is missing required attribute \"$name\"")
}

fun Manifest.requireAttributeValueIn(name: String, vararg values: String?) {
    with (mainAttributes.getValue(name)) {
        if (this !in values)
            throw CordappManifestException("Manifest has invalid attribute \"$name\" value \"$this\"")
    }
}

fun <T> List<T>.firstOrThrow(noElementsException: Exception): T {
    if (isEmpty())
        throw noElementsException
    return this[0]
}

fun <T> List<T>.singleOrThrow(noElementsException: Exception, multipleElementsException: Exception): T {
    return when (size) {
        0 -> throw noElementsException
        1 -> this[0]
        else -> throw multipleElementsException
    }
}