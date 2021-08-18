package net.corda.kotlin.test.api

@Suppress("unused")
interface KotlinApi {
    val nativeLong: Long

    val nullableString: String?

    val nonNullableString: String

    fun anything(): Any?

    fun examine(blobs: Array<Blob>)
}
