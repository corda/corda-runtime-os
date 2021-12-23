package net.corda.crypto.client

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.records.Record
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.bouncycastle.jce.ECNamedCurveTable
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.time.Instant
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

fun generateKeyPair(schemeMetadata: CipherSchemeMetadata): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC", schemeMetadata.providers["BC"])
    keyPairGenerator.initialize(ECNamedCurveTable.getParameterSpec("secp256r1"))
    return keyPairGenerator.generateKeyPair()
}

fun sign(schemeMetadata: CipherSchemeMetadata, privateKey: PrivateKey, data: ByteArray): ByteArray {
    val signature = Signature.getInstance("SHA256withECDSA", schemeMetadata.providers["BC"])
    signature.initSign(privateKey, schemeMetadata.secureRandom)
    signature.update(data)
    return signature.sign()
}

inline fun <reified R> Publisher.act(
    block: () -> R
): PublishActResult<R> {
    val result = actWithTimer(block)
    val messages = argumentCaptor<List<Record<*, *>>>()
    verify(this).publish(messages.capture())
    return PublishActResult(
        before = result.first,
        after = result.second,
        value = result.third,
        messages = messages.allValues
    )
}

inline fun <reified REQUEST: Any, reified RESPONSE: Any, reified RESULT: Any> RPCSender<REQUEST, RESPONSE>.act(
    block: () -> RESULT?
): SendActResult<REQUEST, RESULT> {
    val result = actWithTimer(block)
    val messages = argumentCaptor<REQUEST>()
    verify(this).sendRequest(messages.capture())
    return SendActResult(
        before = result.first,
        after = result.second,
        value = result.third,
        messages = messages.allValues
    )
}

inline fun <reified R> actWithTimer(block: () -> R): Triple<Instant, Instant, R> {
    val before = Instant.now()
    val result = block()
    val after = Instant.now()
    return Triple(before, after, result)
}

fun assertThatIsBetween(actual: Instant, before: Instant, after: Instant) {
    assertThat(
        actual.toEpochMilli(),
        allOf(
            greaterThanOrEqualTo(before.toEpochMilli()),
            lessThanOrEqualTo(after.toEpochMilli())
        )
    )
}

data class PublishActResult<R>(
    val before: Instant,
    val after: Instant,
    val value: R,
    val messages: List<List<Record<*, *>>>
) {
    val firstRecord: Record<*, *> get() = messages.first()[0]

    fun assertThatIsBetween(timestamp: Instant) = assertThatIsBetween(timestamp, before, after)
}

data class SendActResult<REQUEST, RESPONSE>(
    val before: Instant,
    val after: Instant,
    val value: RESPONSE?,
    val messages: List<REQUEST>
) {
    val firstRequest: REQUEST get() = messages.first()

    fun assertThatIsBetween(timestamp: Instant) = assertThatIsBetween(timestamp, before, after)
}

abstract class AbstractComponentTests<COMPONENT: Lifecycle> {
    protected var coordinatorIsRunning = false
    protected lateinit var coordinator: LifecycleCoordinator
    protected lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    protected lateinit var component: COMPONENT

    fun setup(componentFactory: () -> COMPONENT) {
        coordinator = mock {
            on { start() } doAnswer {
                coordinatorIsRunning = true
            }
            on { stop() } doAnswer {
                coordinatorIsRunning = false
            }
            on { isRunning }.thenAnswer { coordinatorIsRunning }
            on { postEvent(any()) } doAnswer {
                val event = it.getArgument(0, LifecycleEvent::class.java)
                component::class.memberFunctions.first { f -> f.name == "handleCoordinatorEvent" }.let { ff ->
                    ff.isAccessible = true
                    ff.call(component, event)
                }
                Unit
            }
        }
        coordinatorFactory = mock {
            on { createCoordinator(any(), any()) } doReturn coordinator
        }
        component = componentFactory()
        component.start()
        coordinator.postEvent(
            RegistrationStatusChangeEvent(
                registration = mock(),
                status = LifecycleStatus.UP
            )
        )
    }
}