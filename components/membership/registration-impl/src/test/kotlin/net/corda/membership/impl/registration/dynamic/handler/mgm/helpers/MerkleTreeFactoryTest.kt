package net.corda.membership.impl.registration.dynamic.mgm.handler.helpers

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.impl.registration.dynamic.handler.helpers.MerkleTreeFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStream

// Implementation should change with once https://github.com/corda/corda-runtime-os/pull/1550 is merged
class MerkleTreeFactoryTest {
    private val serializer = mock<CordaAvroSerializer<KeyValuePairList>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn serializer
    }
    private val hash = mock<SecureHash>()
    private val data = argumentCaptor<InputStream>()
    private val digestService = mock<DigestService> {
        on { hash(data.capture(), eq(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME)) } doReturn hash
    }

    private val factory = MerkleTreeFactory(cordaAvroSerializationFactory, digestService)

    @Test
    fun `buildTree return the correct hash`() {
        val members = (1..3).map {
            val memberContext = mock<MemberContext>()
            val mgmContext = mock<MGMContext>()

            whenever(memberContext.entries).thenReturn(
                mapOf("MGM" to "No+$it").entries
            )
            whenever(mgmContext.entries).thenReturn(
                mapOf("MGM" to "Yes+$it").entries
            )

            whenever(serializer.serialize(memberContext.toAvro())).thenReturn("m$it".toByteArray())
            whenever(serializer.serialize(mgmContext.toAvro())).thenReturn("m$it".toByteArray())
            mock<MemberInfo> {
                on { memberProvidedContext } doReturn memberContext
                on { mgmProvidedContext } doReturn mgmContext
            }
        }

        val tree = factory.buildTree(members)

        assertThat(tree.root).isEqualTo(hash)
    }

    @Test
    fun `buildTree hash the correct data`() {
        val members = (1..3).map {
            val memberContext = mock<MemberContext>()
            val mgmContext = mock<MGMContext>()

            whenever(memberContext.entries).thenReturn(
                mapOf("MGM" to "No+$it").entries
            )
            whenever(mgmContext.entries).thenReturn(
                mapOf("MGM" to "Yes+$it").entries
            )

            whenever(serializer.serialize(memberContext.toAvro())).thenReturn("m$it".toByteArray())
            whenever(serializer.serialize(mgmContext.toAvro())).thenReturn("g$it".toByteArray())
            mock<MemberInfo> {
                on { memberProvidedContext } doReturn memberContext
                on { mgmProvidedContext } doReturn mgmContext
            }
        }

        factory.buildTree(members).root

        assertThat(data.firstValue.reader().readText()).isEqualTo("m1g1m2g2m3g3")
    }

    @Test
    fun `buildTree fail when serialization failed`() {
        val members = (1..3).map {
            val memberContext = mock<MemberContext>()
            val mgmContext = mock<MGMContext>()

            whenever(memberContext.entries).thenReturn(
                mapOf("MGM" to "No+$it").entries
            )
            whenever(mgmContext.entries).thenReturn(
                mapOf("MGM" to "Yes+$it").entries
            )

            whenever(serializer.serialize(memberContext.toAvro())).thenReturn(null)
            whenever(serializer.serialize(mgmContext.toAvro())).thenReturn(null)
            mock<MemberInfo> {
                on { memberProvidedContext } doReturn memberContext
                on { mgmProvidedContext } doReturn mgmContext
            }
        }

        assertThrows<CordaRuntimeException> {
            factory.buildTree(members).root
        }
    }
}
