package net.corda.cpiinfo.read.fake

import net.corda.cpiinfo.read.CpiInfoListener
import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.libs.packaging.CpkIdentifier
import net.corda.libs.packaging.CpkMetadata
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.packaging.CordappManifest
import net.corda.packaging.Cpi
import net.corda.packaging.Cpk
import net.corda.packaging.ManifestCordappInfo
import net.corda.packaging.converters.toCorda
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

internal class CpiInfoReadServiceFakeTest {

    // * VirtualInfo vs CpiInfoRead: get, getById different than get
    // * Implementation of add merging instead of replacing
    // * Load cpi info from yaml
    // * Load cpi from file?

    private val cpi1 = TestCatalogue.Cpi.createMetadata("CPI1", "CPK1")
    private val cpi2 = TestCatalogue.Cpi.createMetadata("CPI2", "CPK2")
    private val cpi3 = TestCatalogue.Cpi.createMetadata("CPI3", "CPK3")

    @Test
    fun `get all cpis`() {
        assertEquals(listOf(cpi1, cpi2), createService(cpi1, cpi2).getAll(), "All CPIs")
        assertEquals(emptyList<CpiMetadata>(), createService().getAll(), "Empty CPIs")
    }

    @Test
    fun `get by id`() {
        val service = createService(cpi1, cpi2)
        assertEquals(cpi2, service.get(cpi2.cpiId), "Cpi Metadata by id")
        assertEquals(null, service.get(cpi3.cpiId), "Cpi Metadata by id")
    }

    @Test
    fun `callback is called immediately when registered`() {
        val listener = Mockito.mock(CpiInfoListener::class.java)
        val service = createService(cpi1)

        service.registerCallback(listener)
        Mockito.verify(listener).onUpdate(changedKeys(cpi1), snapshot(cpi1))
    }

    @Test
    fun `add a cpi info`() {
        val listener = Mockito.mock(CpiInfoListener::class.java)
        val service = createService(cpi1, callbacks = listOf(listener))

        service.addOrUpdate(cpi2)

        assertEquals(listOf(cpi1, cpi2), service.getAll(), "all cpis")
        Mockito.verify(listener).onUpdate(changedKeys(cpi2), snapshot(cpi1, cpi2))
    }

    @Test
    fun `remove cpi info`() {
        val listener = Mockito.mock(CpiInfoListener::class.java)
        val service = createService(cpi1, cpi2, callbacks = listOf(listener))

        service.remove(cpi1.cpiId)

        assertEquals(listOf(cpi2), service.getAll(), "all cpis")
        Mockito.verify(listener).onUpdate(changedKeys(cpi1), snapshot(cpi2))
    }

    private fun createService(
        vararg cpiIds: CpiMetadata,
        callbacks: List<CpiInfoListener> = emptyList(),
    ): CpiInfoReadServiceFake {
        val service = CpiInfoReadServiceFake(
            cpiIds.asIterable(),
            callbacks,
            LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
        )
        service.start()
        service.waitUntilRunning()
        return service
    }

    private fun changedKeys(vararg metadatas: CpiMetadata): Set<Cpi.Identifier> {
        return metadatas.map { it.toAvro().toCorda().id }.toSet()
    }

    private fun snapshot(vararg metadatas: CpiMetadata): Map<Cpi.Identifier, Cpi.Metadata> {
        return metadatas.map { it.toAvro().toCorda() }.associateBy { it.id }
    }
}

object TestCatalogue {
    object Cpi {
        fun createMetadata(cpiName: String, cpkName: String): CpiMetadata {
            return CpiMetadata(
                CpiIdentifier(cpiName, "0.0", SecureHash("ALG", byteArrayOf(0, 0, 0, 0))),
                SecureHash("ALG", byteArrayOf(0, 0, 0, 0)),
                listOf(
                    CpkMetadata(
                        CpkIdentifier(cpkName, "0.0", SecureHash("ALG", byteArrayOf(0, 0, 0, 0))),
                        Cpk.Manifest.newInstance(Cpk.FormatVersion.newInstance(0, 0)),
                        "",
                        listOf(),
                        listOf(),
                        CordappManifest(
                            "",
                            "",
                            0,
                            0,
                            ManifestCordappInfo(null, null, null, null),
                            ManifestCordappInfo(null, null, null, null),
                            mapOf()
                        ),
                        Cpk.Type.UNKNOWN,
                        SecureHash("ALG", byteArrayOf(0, 0, 0, 0)),
                        setOf()
                    )
                ),
                ""
            )
        }
    }
}