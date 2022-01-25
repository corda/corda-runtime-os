@file:JvmName("TestUtils")

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
import java.security.Signature
import java.time.Instant
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

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

abstract class ComponentTestsBase<COMPONENT: Lifecycle> {
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
                component::class.memberFunctions.first { f -> f.name == "eventHandler" }.let { ff ->
                    ff.isAccessible = true
                    ff.call(component, event, coordinator)
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

fun generateKeyPair(schemeMetadata: CipherSchemeMetadata, signatureSchemeName: String): KeyPair {
    val scheme = schemeMetadata.findSignatureScheme(signatureSchemeName)
    val keyPairGenerator = KeyPairGenerator.getInstance(
        scheme.algorithmName,
        schemeMetadata.providers.getValue(scheme.providerName)
    )
    if (scheme.algSpec != null) {
        keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
    } else if (scheme.keySize != null) {
        keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
    }
    return keyPairGenerator.generateKeyPair()
}

fun signData(schemeMetadata: CipherSchemeMetadata, keyPair: KeyPair, data: ByteArray): ByteArray {
    val scheme = schemeMetadata.findSignatureScheme(keyPair.public)
    val signature = Signature.getInstance(
        scheme.signatureSpec.signatureName,
        schemeMetadata.providers[scheme.providerName]
    )
    signature.initSign(keyPair.private, schemeMetadata.secureRandom)
    signature.update(data)
    return signature.sign()
}