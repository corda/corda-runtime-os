package net.corda.ext.api.rpc

import aQute.bnd.annotation.spi.ServiceConsumer
import net.corda.v5.application.messaging.RPCOps
import net.corda.ext.api.NodeRpcOps
import net.corda.ext.api.ExtendedNodeServicesContext
import net.corda.ext.api.rpc.proxies.AuthenticatedRpcOpsProxy
import net.corda.ext.api.rpc.proxies.ThreadContextAdjustingRpcOpsProxy
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import java.util.*

private typealias RpcOpWithVersion = Pair<NodeRpcOps<out RPCOps>, Int?>

/**
 * Responsible for discovery of `RPCOps` server-side implementations, filtering them out as necessary and
 * creating proxies which will enforce permissions and other aspects.
 */
class RpcImplementationsFactory(private val loadingMethod: () -> List<NodeRpcOps<*>> = Companion::defaultLoadingMethod) {

    @ServiceConsumer(NodeRpcOps::class)
    companion object {
        private val logger = contextLogger()

        /** Returns all RPC operation implementations registered via the Service Loader Mediator specification. */
        private fun defaultLoadingMethod() : List<NodeRpcOps<*>> {
            val classloader = FrameworkUtil
                    .getBundle(RpcImplementationsFactory::class.java)
                    ?.adapt(BundleWiring::class.java)
                    ?.classLoader
                    // We fall back to the thread's context classloader outside of an OSGi environment (e.g. for legacy tests).
                    ?: Thread.currentThread().contextClassLoader

            val serviceLoader = ServiceLoader.load(NodeRpcOps::class.java, classloader)
            return try {
                serviceLoader.toList()
            } catch (ex: Throwable) {
                logger.error("Unexpected exception", ex)
                throw IllegalStateException(ex)
            }
        }
    }

    /**
     * Represents container which holds a real implementation for node lifecycle notification purposes along with
     * proxied version of the interface which knows how to perform entitlements check as well as perform some of the actions.
     */
    data class ImplWithProxy(val nodeRpcOps: NodeRpcOps<out RPCOps>, val proxy : RPCOps)

    fun discoverAndCreate(nodeServicesContext: ExtendedNodeServicesContext) : List<ImplWithProxy> {
        val implementationsLoaded: List<NodeRpcOps<*>> = loadingMethod()
        logger.info("Discovered NodeRpcOps count: ${implementationsLoaded.size}")

        val implementationsWithVersions: List<RpcOpWithVersion> = implementationsLoaded.map {
            RpcOpWithVersion(it, try {
                it.getVersion(nodeServicesContext)
            } catch (ex: UnsupportedOperationException) {
                logger.info("Ignoring ${it.javaClass}: ${ex.message}")
                null
            } catch (ex: Exception) {
                logger.warn("Failed to provide version: $it", ex)
                null
            })
        }

        val versionSuccess: List<RpcOpWithVersion> = implementationsWithVersions.filter { it.second != null }

        val groupedByTargetInterface: Map<Class<out RPCOps>, List<RpcOpWithVersion>> = versionSuccess.groupBy { it.first.targetInterface }

        val maxVersionList: List<NodeRpcOps<out RPCOps>> = groupedByTargetInterface.map { intGroup ->
            val listOfSameInterfaceImplementations = intGroup.value
            val maxVersionElement = listOfSameInterfaceImplementations.maxByOrNull { it.second!! }!!
            logMaxVersionImpls(listOfSameInterfaceImplementations, maxVersionElement)
            maxVersionElement.first
        }

        return maxVersionList.map { rpcOpsImpl ->
            // Mind that order of proxies is important
            val targetInterface = rpcOpsImpl.targetInterface
            val stage1Proxy = AuthenticatedRpcOpsProxy.proxy(rpcOpsImpl, targetInterface)
            val stage2Proxy = ThreadContextAdjustingRpcOpsProxy.proxy(stage1Proxy, targetInterface,
                    nodeServicesContext.nodeAdmin.corDappClassLoader)

            ImplWithProxy(rpcOpsImpl, stage2Proxy)
        }
    }

    private fun logMaxVersionImpls(listOfSameInterfaceImplementations: List<RpcOpWithVersion>,
                                   maxVersionElement: RpcOpWithVersion) {
        val targetInterface = maxVersionElement.first.targetInterface
        if(listOfSameInterfaceImplementations.size == 1) {
            logger.info("For $targetInterface there is a single implementation: ${maxVersionElement.first}")
            return
        }

        val eliminatedWithVersions = listOfSameInterfaceImplementations.filterNot { it === maxVersionElement }
                .joinToString { "${it.first} -> ${it.second}" }
        val maxVersion = maxVersionElement.second!!

        logger.info("For $targetInterface maxVersion is: $maxVersion. The winner is: ${maxVersionElement.first}." +
                " Runners-up: $eliminatedWithVersions.")
    }
}
