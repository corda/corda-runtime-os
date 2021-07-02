package net.corda.flow.manager

import net.corda.internal.application.cordapp.Cordapp
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowInfo
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.base.internal.isRegularFile
import net.corda.v5.base.internal.location
import net.corda.v5.base.internal.toPath
import net.corda.v5.base.util.Try
import net.corda.v5.crypto.SecureHash


/**
 * Stored per [SubFlow]. Contains metadata around the version of the code at the Checkpointing moment.
 */
sealed class SubFlowVersion {
    companion object {
        fun createSubFlowVersion(cordapp: Cordapp?, platformVersion: Int): SubFlowVersion {
            return cordapp?.let { CorDappFlow(platformVersion, it.name, it.jarHash) }
                ?: CoreFlow(platformVersion)
        }
    }

    abstract val platformVersion: Int

    data class CoreFlow(override val platformVersion: Int) : SubFlowVersion()
    data class CorDappFlow(override val platformVersion: Int, val corDappName: String, val corDappHash: SecureHash) :
        SubFlowVersion()
}

/**
 * A [SubFlow] contains metadata about a currently executing sub-flow. At any point the flow execution is
 * characterised with a stack of [SubFlow]s. This stack is used to determine the initiating-initiated flow mapping.
 *
 * Note that Initiat*ed*ness is an orthogonal property of the top-level subflow, so we don't store any information about
 * it here.
 */
sealed class SubFlow {
    abstract val flowClass: Class<out Flow<*>>

    // Version of the code.
    abstract val subFlowVersion: SubFlowVersion

    abstract val isEnabledTimedFlow: Boolean

    /**
     * An inlined subflow.
     */
    data class Inlined(
        override val flowClass: Class<Flow<*>>,
        override val subFlowVersion: SubFlowVersion,
        override val isEnabledTimedFlow: Boolean
    ) : SubFlow()

    /**
     * An initiating subflow.
     * @param [flowClass] the concrete class of the subflow.
     * @param [classToInitiateWith] an ancestor class of [flowClass] with the [InitiatingFlow] annotation, to be sent
     *   to the initiated side.
     * @param flowInfo the [FlowInfo] associated with the initiating flow.
     */
    data class Initiating(
        override val flowClass: Class<Flow<*>>,
        val classToInitiateWith: Class<in Flow<*>>,
        val flowInfo: FlowInfo,
        override val subFlowVersion: SubFlowVersion,
        override val isEnabledTimedFlow: Boolean
    ) : SubFlow()

    companion object {
        fun create(
            flowClass: Class<Flow<*>>,
            subFlowVersion: SubFlowVersion,
            isEnabledTimedFlow: Boolean
        ): Try<SubFlow> {
            // Are we an InitiatingFlow?
            val initiatingAnnotations = getInitiatingFlowAnnotations(flowClass)
            return when (initiatingAnnotations.size) {
                0 -> {
                    Try.Success(Inlined(flowClass, subFlowVersion, isEnabledTimedFlow))
                }
                1 -> {
                    val initiatingAnnotation = initiatingAnnotations[0]
                    val flowContext = FlowInfo(initiatingAnnotation.second.version, flowClass.appName)
                    Try.Success(
                        Initiating(
                            flowClass,
                            initiatingAnnotation.first,
                            flowContext,
                            subFlowVersion,
                            isEnabledTimedFlow
                        )
                    )
                }
                else -> {
                    Try.Failure(
                        IllegalArgumentException(
                            "${InitiatingFlow::class.java.name} can only be annotated " +
                                    "once, however the following classes all have the annotation: " +
                                    "${initiatingAnnotations.map { it.first }}"
                        )
                    )
                }
            }
        }

        private fun <C> getSuperClasses(clazz: Class<C>): List<Class<in C>> {
            var currentClass: Class<in C>? = clazz
            val result = ArrayList<Class<in C>>()
            while (currentClass != null) {
                result.add(currentClass)
                currentClass = currentClass.superclass
            }
            return result
        }

        private fun getInitiatingFlowAnnotations(flowClass: Class<Flow<*>>): List<Pair<Class<in Flow<*>>, InitiatingFlow>> {
            return getSuperClasses(flowClass).mapNotNull { clazz ->
                val initiatingAnnotation = clazz.getDeclaredAnnotation(InitiatingFlow::class.java)
                initiatingAnnotation?.let { Pair(clazz, it) }
            }
        }
    }
}

val Class<out Flow<*>>.appName: String
    get() {
        val unknownAppIdentifier = "<unknown>"
        val uri = location.toURI()
        return when (uri.scheme) {
            // OSGi bundles follow this scheme.
            "jar" -> {
                uri.schemeSpecificPart.substringAfterLast('/').removeSuffix(".jar")
            }
            // User-loaded CorDapps follow this scheme.
            "file" -> {
                val jarFile = uri.toPath()
                if (jarFile.isRegularFile() && jarFile.toString().endsWith(".jar")) {
                    jarFile.fileName.toString().removeSuffix(".jar")
                } else {
                    unknownAppIdentifier
                }
            }
            else -> unknownAppIdentifier
        }
    }