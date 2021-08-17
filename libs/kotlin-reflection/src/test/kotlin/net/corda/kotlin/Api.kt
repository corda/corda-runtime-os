package net.corda.kotlin

interface Api {
    val firstVal: String
    var firstVar: String

    fun greet(name: String)

    fun shareApi(item: Any)

    var shareApiProp: String

    var Collection<Any>.apiShow: String
    fun ByteArray.testFunc(obj: Any): Boolean

    fun anything(): Any?
    fun anything(index: Int)
    val primitiveIntVal: Int?

    @Suppress("unused", "MayBeConstant")
    companion object {
        @JvmField
        val apiField: String = "Api"
    }
}
