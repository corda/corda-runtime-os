package net.corda.kotlin

interface ExtraApi : Api {
    val secondVal: String?
    var secondVar: String?

    fun tell(name: String, message: String?): String

    override fun shareApi(item: Any)
    override var shareApiProp: String

    val String.extraApiExtensionProp: String
    fun Long.extraApiExtensionFunc(data: Any?): Any?

    override fun anything(): List<Any?>
    fun anything(message: String, vararg params: Any?): Any
}
