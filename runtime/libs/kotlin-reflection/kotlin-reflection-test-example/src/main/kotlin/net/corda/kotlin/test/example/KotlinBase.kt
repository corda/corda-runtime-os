package net.corda.kotlin.test.example

import net.corda.kotlin.test.api.Blob

@Suppress("unused")
open class KotlinBase {
    open val baseNullable: Any? = null
    open val baseNonNullable: Any = Any()
    protected open val protectedBaseNullable: Any? = null

    fun examine(blobs: Array<Blob>) {
        println(blobs)
    }
}
