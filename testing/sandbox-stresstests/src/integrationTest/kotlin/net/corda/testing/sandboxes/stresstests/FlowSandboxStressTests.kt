package net.corda.testing.sandboxes.stresstests

import net.corda.sandboxgroupcontext.*
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowSandboxStressTests : TestBase() {

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        this.bundleContext = bundleContext
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(TIMEOUT_MILLIS)
            cpiInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
            flowSandboxService = setup.fetchService(TIMEOUT_MILLIS)
        }
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `create sandboxes - no cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes)

        //set cache size to 0
        virtualNode.sandboxGroupContextComponent.resizeCache(SandboxGroupType.FLOW, 0)

        vNodes.forEach {
            val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
        }
    }
    @ParameterizedTest
    @EnumSource(StressTestType::class)
    @Timeout(value = 5, unit = TimeUnit.MINUTES) // This times out for 100 and 250 vnodes; using 5 minutes timeout
    fun `retrieve sandboxes using large cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes)

        //set cache size to 251
        virtualNode.sandboxGroupContextComponent.resizeCache(SandboxGroupType.FLOW, 251)

        vNodes.forEach {
            val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
        }

        // track evictions
        var evictions = 0
        virtualNode.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.FLOW) {
            evictions++
            println("Flow sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        Assertions.assertThat(evictions == 0)

        // retrieve all sandboxes from the cache
        vNodes.forEach {
            val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            println("Pulling sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")

            // TODO: should probably exercise the sandbox somehow
        }

        // no evictions should have happened when retrieving the sandboxes
        Assertions.assertThat(evictions == 0)
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class, names = ["ONE_HUNDRED_SANDBOXES", "TWO_HUNDRED_FIFTY_SANDBOXES"])
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `retrieve sandboxes using small cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes)

        //set cache size to 10
        virtualNode.sandboxGroupContextComponent.resizeCache(SandboxGroupType.FLOW, 10)

        vNodes.forEach {
            val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
        }

        // track evictions
        var evictions = 0
        virtualNode.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.FLOW) {
            evictions++
            println("Flow sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        Assertions.assertThat(evictions == 0)

        // retrieve all sandboxes from the cache
        vNodes.forEach {
            val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            println("Pulling sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")

            // TODO: should probably exercise the sandbox somehow
        }

        // no evictions should have happened when retrieving the sandboxes
        Assertions.assertThat(evictions == 0)
    }





//    @Test
//    fun createSandbox() {
////        flowSandboxService = FlowSandboxServiceFactory().create(
////            virtualNode.sandboxGroupContextComponent,
////            dependencyInjectorFactory,
////            flowProtocolStoreFactory,
////            bundleContext
////        )
//        // create vNodes
//        val vNodes = mutableListOf<VirtualNodeInfo>()
//        repeat(10) {
//            vNodes.add(virtualNode.load(Resources.EXTENDABLE_CPB))
//        }
//
//        //set cache size to 0
//        virtualNode.sandboxGroupContextComponent.resizeCache(SandboxGroupType.FLOW, 0)
//
//        // virtual node context
//        val vNode = vNodes.first()
//        val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(vNode)
////        val vNodeContext = VirtualNodeContext(vNode.holdingIdentity, cpkFileHashes, SandboxGroupType.FLOW, null)
////
////
////        // load cpk into the sandbox group context component
////        // magically done?
////
////        // create sandbox group context from the component and initialise it
////        val sandboxGroupContext = virtualNode.sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, context ->
////            initSandbox(context)
////        }
////
////        val flowSandboxGroupContext = FlowSandboxGroupContextImpl.fromContext(sandboxGroupContext)
////        println(flowSandboxGroupContext)
//        println(flowSandboxService.get(vNode.holdingIdentity, cpkFileHashes))
//        println("DADA")
//    }
//
////    private fun initSandbox(context: MutableSandboxGroupContext): AutoCloseable {
////        val sandboxGroup = context.sandboxGroup
////        val customCrypto = virtualNode.sandboxGroupContextComponent.registerCustomCryptography(context)
////
////        val injectorService = FlowSandboxDependencyInjectorImpl(dependencyInjectorFactory.create(context))
////        context.putObjectByKey("DEPENDENCY_INJECTOR", injectorService)
////
////        val cleanupCordaSingletons = mutableListOf<AutoCloseable>()
////        // Identify singleton services outside the sandbox that may need checkpointing.
////        // These services should not overlap with the injectable services, which should
////        // all have PROTOTYPE scope outside the sandbox.
////        val nonInjectableSingletons = getNonInjectableSingletons(cleanupCordaSingletons)
////        context.putObjectByKey("NON_INJECTABLE_SINGLETONS", nonInjectableSingletons)
////
////        // Build CorDapp serializers
////        // Current implementation has unique serializers per CPI
////        val customSerializers = virtualNode.sandboxGroupContextComponent.registerCordappCustomSerializers(context)
////
////        context.putObjectByKey("FLOW_PROTOCOL_STORE", flowProtocolStoreFactory.create(sandboxGroup))
////
////        // User custom serialization support, no exceptions thrown so user code doesn't kill the flow service
////        val jsonSerializers = virtualNode.sandboxGroupContextComponent.registerCustomJsonSerializers(context)
////        val jsonDeserializers = virtualNode.sandboxGroupContextComponent.registerCustomJsonDeserializers(context)
////
////        // Instruct all CustomMetadataConsumers to accept their metadata.
////        virtualNode.sandboxGroupContextComponent.acceptCustomMetadata(context)
////
////        return AutoCloseable {
////            jsonDeserializers.close()
////            jsonSerializers.close()
////            cleanupCordaSingletons.forEach(AutoCloseable::close)
////            customSerializers.close()
////            injectorService.close()
////            customCrypto.close()
////        }
////    }

//    private fun getNonInjectableSingletons(cleanups: MutableList<AutoCloseable>): Set<SingletonSerializeAsToken> {
//        // An OSGi singleton component can still register bundle-scoped services, so
//        // select the non-prototype ones here. They should all be internal to Corda.
//        return bundleContext.getServiceReferences(SingletonSerializeAsToken::class.java, "(!(${Constants.SERVICE_SCOPE}=${Constants.SCOPE_PROTOTYPE}))")
//            .mapNotNullTo(linkedSetOf()) { ref ->
//                bundleContext.getService(ref)?.also {
//                    cleanups.add(AutoCloseable { bundleContext.ungetService(ref) })
//                }
//            }
//    }
}