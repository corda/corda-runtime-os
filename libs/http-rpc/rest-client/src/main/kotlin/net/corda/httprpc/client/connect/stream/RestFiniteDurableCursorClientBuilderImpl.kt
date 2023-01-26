package net.corda.httprpc.client.connect.stream

import net.corda.httprpc.client.auth.RequestContext
import net.corda.httprpc.client.config.AuthenticationConfig
import net.corda.httprpc.client.connect.remote.RemoteClient
import net.corda.httprpc.client.processing.endpointHttpVerb
import net.corda.httprpc.client.processing.parametersFrom
import net.corda.httprpc.client.processing.toWebRequest
import net.corda.httprpc.client.stream.InMemoryPositionManager
import net.corda.httprpc.client.stream.TypeUtils
import net.corda.httprpc.durablestream.api.Cursor
import net.corda.httprpc.durablestream.api.FiniteDurableCursor
import net.corda.httprpc.durablestream.api.FiniteDurableCursorBuilder
import net.corda.httprpc.durablestream.api.PositionManager
import net.corda.v5.base.util.trace
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.time.Duration

@Suppress("LongParameterList")
internal class RestFiniteDurableCursorClientBuilderImpl(
    private val client: RemoteClient,
    private val method: Method,
    private val rawPath: String,
    private val args: Array<out Any?>?,
    private val authenticationConfig: AuthenticationConfig
) : FiniteDurableCursorBuilder<Any> {
    override var positionManager: PositionManager = InMemoryPositionManager()

    override fun build(): FiniteDurableCursor<Any> =
        RestFiniteDurableCursorClientImpl(client, method, rawPath, args, authenticationConfig, positionManager)
}

@Suppress("LongParameterList")
private class RestFiniteDurableCursorClientImpl(
    private val client: RemoteClient,
    private val method: Method,
    private val rawPath: String,
    private val args: Array<out Any?>?,
    private val authenticationConfig: AuthenticationConfig,
    override val positionManager: PositionManager
) : FiniteDurableCursor<Any> {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun poll(maxCount: Int, awaitForResultTimeout: Duration): Cursor.PollResult<Any> {
        log.trace { "Poll maxCount: $maxCount, timeout: $awaitForResultTimeout." }
        val parameters = method.parametersFrom(
            args, mapOf("context" to TypeUtils.durableStreamContext(positionManager.get(), maxCount))
        )

        val methodParameterizedType = method.genericReturnType as ParameterizedType
        val itemType = methodParameterizedType.actualTypeArguments[0]
        val pollResultParamType = TypeUtils.parameterizePollResult(itemType)

        val response = client.call(
            method.endpointHttpVerb, parameters.toWebRequest(rawPath),
            pollResultParamType, RequestContext.fromAuthenticationConfig(authenticationConfig)
        )
        return uncheckedCast(response.body!!)
    }
}