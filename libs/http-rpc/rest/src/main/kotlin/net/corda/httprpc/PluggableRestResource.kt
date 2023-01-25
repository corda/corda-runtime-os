package net.corda.httprpc

/**
 * Defines an interface for `RestResource` that can be discovered during startup.
 * The idea is that there can be multiple implementations of the same RestResource interface in the classpath.
 * Upon start-up the process should be able to select desired implementation of RestResource interface, selection will be done
 * using maximum possible version out of all discovered implementations.
 */
interface PluggableRestResource<T : RestResource> : RestResource {

    /**
     * Specifies the main interface which plugin implements and to which versioning applies.
     */
    val targetInterface: Class<T>
}