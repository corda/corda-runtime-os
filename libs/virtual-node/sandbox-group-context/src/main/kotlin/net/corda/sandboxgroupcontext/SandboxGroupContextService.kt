package net.corda.sandboxgroupcontext

import net.corda.packaging.CPK

interface SandboxGroupContextService {
    /**
     * This function creates and returns a "fully constructed" node that is ready to
     * execute a flow with no further configuration required.  Your supplied initializer will be run
     * during the [getOrCreate] call.
     *
     * A mutable interface is passed so that the initializer can add per CPI custom objects that are
     * cached together with their SandboxGroup.  The initializer must return an `AutoCloseable` object.
     *
     * The lifetime of the [SandboxGroupContext] will be managed by the [SandboxGroupContextService]
     * implementation subject to some lifetime policy (e.g. resource pressure).
     *
     * Your initializer should be idempotent across a process, and also across different processes running on the same
     * system, i.e. for a given set of identical inputs, the constructed objects should be identical in terms of data.
     *
     * This function will be implemented as a pure function, i.e. if the "virtual node" already exists an
     * is constructed, that should be returned, and the [initializer] will not be run on subsequent calls
     * of [getOrCreate].
     *
     * Initializers (functional interface [SandboxGroupContextInitializer]) are expected to be:
     *
     *     fun initialize(holdingIdentity: HoldingIdentity, ctx: MutableSandboxGroupContext) : AutoCloseable {
     *       val someObject = SomeObject() // : AutoCloseable
     *       val otherObject = OtherObject() // : AutoCloseable
     *       val anotherObject = AnotherObject() // NOT AutoCloseable
     *       ctx.putObject(someKey, someObject)
     *       ctx.putObject(otherObjectKey, otherObject)
     *       ctx.putUniqueObject(anotherObject)
     *       return object : AutoCloseable {
     *         override fun close() {
     *           someObject.close()
     *           otherObject.close()
     *         }
     *       }
     *     }
     *
     *     private var ctx : SandboxGroupContext? = null
     *
     *     fun setUp() {
     *       if (! service.hasCpks(cpkIdentifiers)) { /* some retry logic */ }
     *       ctx = service.getOrCreate(VirtualNodeContext(holdingIdentity, cpkIdentifiers, type), ::initialize)
     *       val someObject = ctx!!.getObject<SomeObject>(someKey)
     *       val anotherObject = ctx!!.getUniqueObject<AnotherObject>()
     *       someObject.doThing()
     *       anotherObject.doSomethingElse()
     *    }
     *
     *    fun doSomethingElse(virtualNodeContext: VirtualNodeContext) {
     *       ctx!!.getObject<SomeObject>(someKey)!!.doThing()
     *       ctx!!.getUniqueObject<AnotherObject>()!!.doSomethingElse()
     *    }
     *
     * @throws Exception or "something" if the requested [CPK]s cannot be found in the local package cache.
     *
     * @return a non-null [SandboxGroupContext] instance
     */
    fun getOrCreate(
        virtualNodeContext: VirtualNodeContext,
        initializer: SandboxGroupContextInitializer
    ): SandboxGroupContext

    /**
     * This function registers instances of the service classes
     * identified within the [SandboxGroup]'s [CPK.Metadata].
     * Each OSGi service will be a singleton containing an extra
     * `corda.sandbox=true` service property.
     *
     * Each service is always registered as an instance of
     * [VirtualNodeContext.serviceMarkerType]. If the service
     * is already an OSGi component then it is also registered
     * with that component's service interfaces. Otherwise it
     * is also registered with the service's class.
     *
     * The OSGi framework itself will insist that each metadata
     * service also implements [VirtualNodeContext.serviceMarkerType].
     *
     * You should register these metadata services as part of the
     * [ServiceContextGroupInitializer].
     *
     * @param sandboxGroupContext
     * @param serviceNames A lambda to extract the service class names from the CPK metadata.
     * @param isMetadataService A lambda to validate that each class is compatible with this metadata service type.
     * @param serviceMarkerType A common base type that all service implementations must share.
     *
     * @return an [AutoCloseable] for unregistering the services.
     *
     */
    fun registerMetadataServices(
        sandboxGroupContext: SandboxGroupContext,
        serviceNames: (CPK.Metadata) -> Iterable<String>,
        isMetadataService: (Class<*>) -> Boolean = { true },
        serviceMarkerType: Class<*> = sandboxGroupContext.virtualNodeContext.serviceMarkerType
    ): AutoCloseable

    /**
     * This function registers any [DigestAlgorithmFactory][net.corda.v5.cipher.suite.DigestAlgorithmFactory]
     * instances that exist inside the [SandboxGroup][net.corda.sandbox.SandboxGroup]'s CPKs.
     * The [DigestAlgorithmFactoryProvider][net.corda.crypto.DigestAlgorithmFactoryProvider]
     * component will discover these services when the sandbox uses its
     * [DigestService][net.corda.v5.crypto.DigestService] for the first time.
     *
     * @param sandboxGroupContext
     *
     * @return an [AutoCloseable] for unregistering the services.
     */
    fun registerCustomCryptography(
        sandboxGroupContext: SandboxGroupContext
    ): AutoCloseable

    /**
     * Does the service 'contain' the cpks in its cache?
     *
     *     if (service.hasCpks(virtualNodeContext.cpkIdentifiers)) {
     *        service.getOrCreate(virtualNodeContext) { .... }
     *     }
     */
    fun hasCpks(cpkIdentifiers: Set<CPK.Identifier>) : Boolean
}
