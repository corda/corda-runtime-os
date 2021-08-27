package net.corda.v5.httprpc.api.annotations

/**
 * Marks an interface extending `RPCOps` to be exposed as an HTTP resource.
 *
 * @property name The name of the resource, used for documentation. Defaults to the class name.
 * @property description The description of the resource, used for documentation. Defaults to empty string.
 * @property path The endpoint path of the resource. All exposed functions of the annotated class will have their path prepended with this. Defaults to the class name.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class HttpRpcResource(
        val name: String = "",
        val description: String = "",
        val path: String = ""
)
