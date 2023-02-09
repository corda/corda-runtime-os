package net.corda.p2p.gateway.messaging.mtls

import net.corda.data.p2p.mtls.gateway.ClientCertificateSubjects
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DynamicCertificateSubjectStoreTest {
    private companion object {
        const val TOPIC = "Topic"
        const val KEY_1 = "key1"
        const val KEY_2 = "key2"
        const val SUBJECT_1 = "O=Alice, L=London, C=GB"
        const val SUBJECT_2 = "O=Bob, L=London, C=GB"
        const val SUBJECT_3 = "O=Carol, L=London, C=GB"
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val nodeConfiguration = mock<SmartConfig>()
    private val subscription = mock<CompactedSubscription<String, ClientCertificateSubjects>>()

    private val processor = argumentCaptor<CompactedProcessor<String, ClientCertificateSubjects>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processor.capture(), eq(nodeConfiguration)) } doReturn subscription
    }
    private val subscriptionDominoTile = Mockito.mockConstruction(SubscriptionDominoTile::class.java) { mock, context ->
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as (() -> CompactedSubscription<String, ClientCertificateSubjects>)).invoke()
    }
    private val dynamicCertificateSubjectStore =
        DynamicCertificateSubjectStore(lifecycleCoordinatorFactory, subscriptionFactory, nodeConfiguration)

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
    }

    @Test
    fun `onSnapshot adds subjects to the store`() {
        processor.firstValue.onSnapshot(mapOf(KEY_1 to ClientCertificateSubjects(SUBJECT_1), KEY_2 to ClientCertificateSubjects(SUBJECT_2)))

        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_1))).isTrue
        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_2))).isTrue
        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_3))).isFalse
    }

    @Test
    fun `onNext adds subjects to the store`() {
        processor.firstValue.onNext(Record(TOPIC, KEY_1, ClientCertificateSubjects(SUBJECT_1)), null, emptyMap())
        processor.firstValue.onNext(Record(TOPIC, KEY_2, ClientCertificateSubjects(SUBJECT_2)), null, emptyMap())

        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_1))).isTrue
        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_2))).isTrue
        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_3))).isFalse
    }

    @Test
    fun `onNext removes subjects from the store after a tombstone record for every key`() {
        processor.firstValue.onNext(Record(TOPIC, KEY_1, ClientCertificateSubjects(SUBJECT_1)), null, emptyMap())
        processor.firstValue.onNext(Record(TOPIC, KEY_2, ClientCertificateSubjects(SUBJECT_1)), null, emptyMap())

        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_1))).isTrue

        processor.firstValue.onNext(Record(TOPIC, KEY_1, null), ClientCertificateSubjects(SUBJECT_1), emptyMap())

        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_1))).isTrue

        processor.firstValue.onNext(Record(TOPIC, KEY_2, null), ClientCertificateSubjects(SUBJECT_1), emptyMap())

        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_1))).isFalse
    }

    @Test
    fun `onNext will add the normalized subject`() {
        processor.firstValue.onNext(
            Record(
                TOPIC,
                KEY_1,
                ClientCertificateSubjects(
                    "C=GB,  O=Alice, L=London"
                )
            ),
            null,
            emptyMap(),
        )

        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_1))).isTrue
    }

    @Test
    fun `onNext will remove the normalized subject`() {
        processor.firstValue.onNext(
            Record(
                TOPIC,
                KEY_1,
                ClientCertificateSubjects(
                    SUBJECT_1
                )
            ),
            null,
            emptyMap(),
        )
        processor.firstValue.onNext(
            Record(
                TOPIC,
                KEY_1,
                null,
            ),
            ClientCertificateSubjects(
                "C=GB,  O=Alice, L=London"
            ),
            emptyMap(),
        )

        assertThat(dynamicCertificateSubjectStore.subjectAllowed(MemberX500Name.parse(SUBJECT_1))).isFalse
    }
}