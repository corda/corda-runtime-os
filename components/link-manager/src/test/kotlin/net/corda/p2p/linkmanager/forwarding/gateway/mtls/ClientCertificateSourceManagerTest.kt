package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class ClientCertificateSourceManagerTest {
    private val listener = mock<ClientCertificateSourceManager.Listener>()
    private val manager = ClientCertificateSourceManager(
        listener
    )

    @Test
    fun `addSource for the first time will publish the source`() {
        manager.addSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key")
        )

        verify(listener).publishSubject("subject")
    }

    @Test
    fun `addSource for the second time will unpublished the source`() {
        manager.addSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key")
        )
        manager.addSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key2")
        )

        verify(listener, times(1)).publishSubject("subject")
    }

    @Test
    fun `removeSource for unknown source will do nothing`() {
        manager.removeSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key")
        )

        verifyNoInteractions(listener)
    }

    @Test
    fun `removeSource for the first time will not unpublished it`() {
        manager.addSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key1")
        )
        manager.addSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key2")
        )

        manager.removeSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key2")
        )

        verify(listener, never()).removeSubject("subject")
    }

    @Test
    fun `removeSource for the all sources time will unpublished it`() {
        manager.addSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key1")
        )
        manager.addSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key2")
        )
        manager.removeSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key2")
        )

        manager.removeSource(
            "subject",
            ClientCertificateSourceManager.MembershipSource("key1")
        )

        verify(listener).removeSubject("subject")
    }
}
