package net.corda.rest.annotations

import net.corda.rest.annotations.RestApiVersion.C5_0 as MIN_SUPPORTED
import net.corda.rest.annotations.RestApiVersion.C5_3 as CURRENT

/**
 * Marks an interface extending `RestResource` to be exposed as an HTTP resource.
 *
 * @property name The name of the resource, used for documentation. Defaults to the class name.
 * @property description The description of the resource, used for documentation. Defaults to empty string.
 * @property path The endpoint path of the resource. All exposed functions of the annotated class will have their path
 * prepended with this. Defaults to the class name.
 * @property minVersion version when API has been introduced.
 * @property maxVersion version till which API is still supported.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SuppressWarnings("LongParameterList")
annotation class HttpRestResource(
    val name: String = "",
    val description: String = "",
    val path: String = "",
    val minVersion: RestApiVersion = MIN_SUPPORTED,
    val maxVersion: RestApiVersion = CURRENT
)
