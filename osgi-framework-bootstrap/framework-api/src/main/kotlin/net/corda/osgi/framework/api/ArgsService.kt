package net.corda.osgi.framework.api

/**
 * The interface provides the service to export `main(args: Array<String>)` parameters to the bundles wrapped by
 * [net.corda.osgi.framework.OSGiFrameworkWrap].
 */
fun interface ArgsService {

    /**
     * Return the `args` registered with [net.corda.osgi.framework.OSGiFrameworkWrap.setArguments].
     *
     * See [OSGi Core r7 5.2.2 Service Interface](http://docs.osgi.org/specification/osgi.core/7.0.0/framework.service.html).
     *
     * In OSGi bundles get the `args` with...
     * ```
     * val serviceReference = bundleContext.getServiceReference(ArgsService::class.java)
     * val argsService = bundleContext.getService(serviceReference)
     * val args = argsService.getArgs()
     * ```
     *
     * @return the `args` registered with [net.corda.osgi.framework.OSGiFrameworkWrap.setArguments].
     *
     * @see [net.corda.osgi.framework.OSGiFrameworkWrap.setArguments]
     */
    fun getArgs(): Array<String>

}