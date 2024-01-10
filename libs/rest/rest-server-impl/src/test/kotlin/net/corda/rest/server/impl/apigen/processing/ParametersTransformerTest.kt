package net.corda.rest.server.impl.apigen.processing

import net.corda.v5.base.annotations.CordaSerializable
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance

internal class ParametersTransformerTest {

    @Test
    fun `create WithNoAnnotations returnsBodyTransformer`() {
        val param = mock<KParameter>().also {
            doReturn(emptyList<Annotation>()).whenever(it).annotations
        }

        assert(ParametersTransformerFactory.create(param) is BodyParametersTransformer)
    }

    @Test
    fun `create WithMultipleAnnotations withoutBodyAnnotation returnsBodyTransformer`() {
        val param = mock<KParameter>().also {
            doReturn(listOf(CordaSerializable::class.createInstance())).whenever(it).annotations
        }

        assert(ParametersTransformerFactory.create(param) is BodyParametersTransformer)
    }
}