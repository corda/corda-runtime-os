package net.corda.configuration.read.impl

import net.corda.data.Fingerprint
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.avro.Schema
import org.apache.avro.SchemaNormalization
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyList
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AvroSchemaProcessorTest {
    private val dogSchemaJson = """
            {
               "type" : "record",
               "namespace" : "zoo",
               "name" : "dog",
               "fields" : [
                  { "name" : "name" , "type" : "string" },
                  { "name" : "age" , "type" : "int" }
               ]
            }
        """.trimIndent()
    private val dogSchema = Schema.Parser().parse(dogSchemaJson)
    private val dogSchemaFingerPrint =
        Fingerprint(SchemaNormalization.parsingFingerprint("SHA-256", dogSchema))

    private val catSchemaJson = """
            {
               "type" : "record",
               "namespace" : "zoo",
               "name" : "cat",
               "fields" : [
                  { "name" : "name" , "type" : "string" },
                  { "name" : "numberOfEars" , "type" : "int" }
               ]
            }
        """.trimIndent()
    private val catSchema = Schema.Parser().parse(catSchemaJson)
    private val catSchemaFingerPrint =
        Fingerprint(SchemaNormalization.parsingFingerprint("SHA-256", catSchema))

    private val coordinator = mock<LifecycleCoordinator>()
    private val avroSchemaRegistry = mock<AvroSchemaRegistry> {
        on { schemasByFingerprintSnapshot } doReturn (mapOf(dogSchemaFingerPrint to dogSchema))
        on { containsSchema(dogSchemaFingerPrint) } doReturn (true)
        on { containsSchema(catSchemaFingerPrint) } doReturn (false)
    }
    private val publisher = mock<Publisher>()

    @Test
    fun `onSnapshot adds missing schemas to registry`() {
        val processor = AvroSchemaProcessor(coordinator, avroSchemaRegistry)

        processor.onSnapshot(
            mapOf(
                dogSchemaFingerPrint to dogSchemaJson,
                catSchemaFingerPrint to catSchemaJson
            )
        )

        verify(avroSchemaRegistry).addSchemaOnly(catSchema)
        verify(avroSchemaRegistry, never()).addSchemaOnly(dogSchema)
    }

    @Test
    fun `onNext adds missing schema to registry`() {
        val processor = AvroSchemaProcessor(coordinator, avroSchemaRegistry)

        processor.onNext(
            Record("", catSchemaFingerPrint, catSchemaJson),
            null,
            emptyMap()
        )

        verify(avroSchemaRegistry).addSchemaOnly(catSchema)
    }

     @Test
    fun `publishNewSchemas publishes schemas added during onSnapshot`() {
         val processor = AvroSchemaProcessor(coordinator, avroSchemaRegistry)

         processor.onSnapshot(mapOf(catSchemaFingerPrint to catSchemaJson))

         processor.publishNewSchemas(publisher)

         whenever(publisher.publish(any())).doAnswer {
             // verifying early as the list gets cleared
             @Suppress("UNCHECKED_CAST")
             val records = it.arguments[0] as List<Record<Fingerprint,String>>
             // only 1 record
             assertThat(records.size).isEqualTo(1)
             // record is cat
             val record = records.single()
             assertSoftly { a ->
                 a.assertThat(record.key).isEqualTo(catSchemaFingerPrint)
                 a.assertThat(record.value).isEqualTo(catSchemaJson)
             }
             null
         }

         verify(publisher).publish(anyList())
    }
}