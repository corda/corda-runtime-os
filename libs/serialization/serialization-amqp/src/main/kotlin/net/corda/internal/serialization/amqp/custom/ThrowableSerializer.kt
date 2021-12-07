package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.LocalSerializerFactory
import net.corda.internal.serialization.amqp.PropertyReader
import net.corda.internal.serialization.amqp.currentSandboxGroup
import net.corda.internal.serialization.model.LocalConstructorInformation
import net.corda.internal.serialization.model.LocalTypeInformation
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.SerializationContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.exceptions.CordaThrowable
import net.corda.v5.base.util.contextLogger
import java.io.NotSerializableException

@Suppress("LongParameterList")
class ThrowableSerializer(
    private val factory: LocalSerializerFactory
) : BaseProxySerializer<Throwable, ThrowableSerializer.ThrowableProxy>() {
    override val type: Class<Throwable> get() = Throwable::class.java
    override val proxyType: Class<ThrowableProxy> get() = ThrowableProxy::class.java
    override val withInheritance: Boolean get() = true
    override val revealSubclasses: Boolean get() = true

    companion object {
        private val logger = contextLogger()
    }

    private val LocalTypeInformation.constructor: LocalConstructorInformation
        get() = when (this) {
            is LocalTypeInformation.NonComposable ->
                constructor ?: throw NotSerializableException("$this has no deserialization constructor")
            is LocalTypeInformation.Composable -> constructor
            is LocalTypeInformation.Opaque -> wrapped.constructor
            else -> throw NotSerializableException("$this has no deserialization constructor")
        }

    override fun toProxy(obj: Throwable): ThrowableProxy {
        val extraProperties: MutableMap<String, Any?> = LinkedHashMap()
        val message = if (obj is CordaThrowable) {
            // Try and find a constructor
            try {
                val typeInformation = factory.getTypeInformation(obj.javaClass)
                extraProperties.putAll(
                    typeInformation.propertiesOrEmptyMap.mapValues { (_, property) ->
                        PropertyReader.make(property).read(obj)
                    }
                )
            } catch (e: NotSerializableException) {
                logger.warn("Unexpected exception", e)
            }
            obj.originalMessage
        } else {
            obj.message
        }
        return ThrowableProxy(obj.javaClass.name, message, obj.stackTrace, obj.cause, obj.suppressed, extraProperties)
    }

    @Suppress("NestedBlockDepth")
    override fun fromProxy(proxy: ThrowableProxy, context: SerializationContext): Throwable {
        try {
            val clazz = context.currentSandboxGroup().loadClassFromMainBundles(proxy.exceptionClass)

            // If it is a CordaRuntimeException, we can seek any constructor and then set the properties
            // Otherwise we just make a CordaRuntimeException
            if (CordaThrowable::class.java.isAssignableFrom(clazz) && Throwable::class.java.isAssignableFrom(clazz)) {
                val typeInformation = factory.getTypeInformation(clazz)
                val constructor = typeInformation.constructor
                val params = constructor.parameters.map { parameter ->
                    proxy.additionalProperties[parameter.name]
                        ?: proxy.additionalProperties[parameter.name.capitalize()]
                }
                val throwable = constructor.observedMethod.newInstance(*params.toTypedArray())
                (throwable as CordaThrowable).apply {
                    if (this.javaClass.name != proxy.exceptionClass) this.originalExceptionClassName = proxy.exceptionClass
                    this.setMessage(proxy.message)
                    this.setCause(proxy.cause)
                    this.addSuppressed(proxy.suppressed)
                }
                return (throwable as Throwable).apply {
                    this.stackTrace = proxy.stackTrace
                }
            }
        } catch (e: Exception) {
            logger.warn("Unexpected exception de-serializing throwable: ${proxy.exceptionClass}. Converting to CordaRuntimeException.", e)
        }
        // If the criteria are not met or we experience an exception constructing the exception,
        // we fall back to our own unchecked exception.
        return CordaRuntimeException(proxy.exceptionClass, null, null)
            .apply {
                this.setMessage(proxy.message)
                this.setCause(proxy.cause)
                this.stackTrace = proxy.stackTrace
                this.addSuppressed(proxy.suppressed)
            }
    }

    class ThrowableProxy(
        val exceptionClass: String,
        val message: String?,
        val stackTrace: Array<StackTraceElement>,
        val cause: Throwable?,
        val suppressed: Array<Throwable>,
        val additionalProperties: Map<String, Any?>
    )

    override fun fromProxy(proxy: ThrowableProxy) = throw UnsupportedOperationException()
}

