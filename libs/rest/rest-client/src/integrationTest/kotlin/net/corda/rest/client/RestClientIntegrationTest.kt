package net.corda.rest.client

import net.corda.rest.client.config.RestClientConfig
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.server.impl.RestServerImpl
import net.corda.rest.test.CalendarRestResource
import net.corda.rest.test.CalendarRestResourceImpl
import net.corda.rest.test.CustomSerializationAPI
import net.corda.rest.test.CustomSerializationAPIImpl
import net.corda.rest.test.CustomString
import net.corda.rest.test.NumberSequencesRestResource
import net.corda.rest.test.NumberSequencesRestResourceImpl
import net.corda.rest.test.NumberTypeEnum
import net.corda.rest.test.TestEntityRestResource
import net.corda.rest.test.TestEntityRestResourceImpl
import net.corda.rest.test.TestHealthCheckAPI
import net.corda.rest.test.TestHealthCheckAPIImpl
import net.corda.test.util.eventually
import net.corda.utilities.NetworkHostAndPort
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.DayOfWeek
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import net.corda.rest.HttpFileUpload
import net.corda.rest.test.TestFileUploadAPI
import net.corda.rest.test.TestFileUploadImpl
import net.corda.rest.test.utils.ChecksumUtil.generateChecksum
import net.corda.rest.test.utils.multipartDir

internal class RestClientIntegrationTest : RestIntegrationTestBase() {
    companion object {

        @BeforeAll
        @JvmStatic
        @Suppress("Unused")
        fun setUpBeforeClass() {
            val restServerSettings = RestServerSettings(
                NetworkHostAndPort("localhost", 0),
                context,
                null,
                null,
                RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
                20000L
            )
            server = RestServerImpl(
                listOf(
                    TestHealthCheckAPIImpl(),
                    CustomSerializationAPIImpl(),
                    NumberSequencesRestResourceImpl(),
                    CalendarRestResourceImpl(),
                    TestEntityRestResourceImpl(),
                    TestFileUploadImpl()
                ),
                ::securityManager,
                restServerSettings,
                multipartDir,
                true
            ).apply { start() }
        }

        @AfterAll
        @JvmStatic
        @Suppress("Unused")
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.close()
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client against server with accepted protocol version succeeds`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
            healthCheckInterval = 500
        )

        val connected = AtomicBoolean()

        val listener = object : RestConnectionListener<TestHealthCheckAPI> {
            override fun onConnect(context: RestConnectionListener.RestConnectionContext<TestHealthCheckAPI>) {
                connected.set(true)
            }

            override fun onDisconnect(context: RestConnectionListener.RestConnectionContext<TestHealthCheckAPI>) {
                connected.set(false)
            }

            override fun onPermanentFailure(context: RestConnectionListener.RestConnectionContext<TestHealthCheckAPI>) {
                fail("Call to onPermanentFailure not expected")
            }
        }
        client.addConnectionListener(listener)

        client.use {
            val connection = client.start()

            eventually {
                assertTrue(connected.get())
            }

            with(connection.proxy) {
                assertEquals(3, this.plus(2L))
                assertEquals(Unit::class.java, this.voidResponse()::class.java)
                assertEquals("Pong for str = value", this.ping(TestHealthCheckAPI.PingPongData("value")))
                assertEquals(listOf(2.0, 3.0, 4.0), this.plusOne(listOf("1", "2", "3")))
                assertEquals(2L, this.plus(1L))
            }
        }

        eventually {
            assertFalse(connected.get())
        }
        client.removeConnectionListener(listener)
    }

    @Test
    @Timeout(100)
    fun `return list of complex types`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
        )

        client.use {
            val connection = client.start()

            with(connection.proxy) {
                val daysCount = 10
                val year = 2021
                val result = firstDaysOfTheYear(year, daysCount)
                val calendar = GregorianCalendar().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.HOUR_OF_DAY, 10)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val expected = (1..daysCount).map { TestHealthCheckAPI.DateCallDto(calendar.apply { set(Calendar.DAY_OF_YEAR, it) }.time) }
                assertThat(result).isEqualTo(expected)
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client against server with accepted protocol version and custom serializers succeeds`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            CustomSerializationAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password))
        )

        client.use {
            val connection = client.start()
            with(connection.proxy) {
                assertEquals("custom custom test", this.printString(CustomString("test")).s)
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client against server with accepted protocol version and infinite durable streams call succeeds`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            NumberSequencesRestResource::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password))
        )

        client.use {
            val connection = client.start()
            with(connection.proxy) {
                val cursor = this.retrieve(NumberTypeEnum.EVEN).build()
                with(cursor.poll(100, 100.seconds)) {
                    assertEquals(100, values.size)
                    assert(values.first() == 0L)
                    assert(values.last() == 198L)
                    assertFalse(this.isLastResult)
                    cursor.commit(this)
                }

                with(cursor.poll(200, 100.seconds)) {
                    assertEquals(200, values.size)
                    assert(values.first() == 200L)
                    assert(values.last() == 598L)
                    assertFalse(this.isLastResult)
                    // Committed not the last
                    cursor.commit(positionedValues[2].position) // 204
                }

                with(cursor.poll(2, 100.seconds)) {
                    assertEquals(2, values.size)
                    assert(values.first() == 206L)
                    assert(values.last() == 208L)
                    assertFalse(this.isLastResult)
                    cursor.commit(this)
                }

                // different cursors on the same function have different positions
                val otherCursor = this.retrieve(NumberTypeEnum.EVEN).build()
                with(otherCursor.poll(100, 100.seconds)) {
                    assertEquals(100, values.size)
                    assert(values.first() == 0L)
                    assert(values.last() == 198L)
                    assertFalse(this.isLastResult)
                    cursor.commit(this)
                }
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client against server with accepted protocol version and finite durable streams call succeeds`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            CalendarRestResource::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password))
        )

        client.use {
            val connection = client.start()
            with(connection.proxy) {
                val cursor = this.daysOfTheYear(2020).build()
                with(cursor.poll(100, 100.seconds)) {
                    assertEquals(100, values.size)
                    assertEquals(CalendarRestResource.CalendarDay(DayOfWeek.WEDNESDAY, "2020-01-01"), values.first())
                    assertEquals(CalendarRestResource.CalendarDay(DayOfWeek.THURSDAY, "2020-04-09"), values.last())
                    assertFalse(this.isLastResult)
                    // no commit
                }

                with(cursor.poll(300, 100.seconds)) {
                    assertEquals(300, values.size)
                    assertEquals(CalendarRestResource.CalendarDay(DayOfWeek.WEDNESDAY, "2020-01-01"), values.first())
                    assertEquals(CalendarRestResource.CalendarDay(DayOfWeek.MONDAY, "2020-10-26"), values.last())
                    assertFalse(this.isLastResult)
                    cursor.commit(this)
                }

                with(cursor.poll(100, 100.seconds)) {
                    assertEquals(66, values.size)
                    assertEquals(CalendarRestResource.CalendarDay(DayOfWeek.TUESDAY, "2020-10-27"), values.first())
                    assertEquals(CalendarRestResource.CalendarDay(DayOfWeek.THURSDAY, "2020-12-31"), values.last())
                    assertTrue(this.isLastResult)
                    cursor.commit(this)
                }
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client against server with less than rest version since but valid version for the resource fails only on the unsupported call`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password))
        )

        client.use {
            val connection = client.start()
            with(connection.proxy) {
                assertEquals(3, this.plus(2L))
                assertEquals(Unit::class.java, this.voidResponse()::class.java)
                assertEquals("Pong for str = value", this.ping(TestHealthCheckAPI.PingPongData("value")))
                assertEquals(listOf(2.0, 3.0, 4.0), this.plusOne(listOf("1", "2", "3")))
                assertEquals(2L, this.plus(1L))
                assertThatThrownBy {
                    this.laterAddedCall()
                }.isInstanceOf(UnsupportedOperationException::class.java)
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client against server with lower protocol version than minimum expected fails`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(3)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password))
        )

        assertThatThrownBy { client.start() }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @Timeout(100)
    fun `operations on TestEntity`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestEntityRestResource::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
        )

        client.use {
            val connection = client.start()

            with(connection.proxy) {

                SoftAssertions.assertSoftly {
                    it.assertThat(create(TestEntityRestResource.CreationParams("TestName", 20)))
                        .isEqualTo("Created using: CreationParams(name=TestName, amount=20)")

                    it.assertThat(getUsingPath("MyId")).isEqualTo("Retrieved using id: MyId")

                    it.assertThat(getUsingQuery("MyQuery")).isEqualTo("Retrieved using query: MyQuery")

                    it.assertThat(update(TestEntityRestResource.UpdateParams("myId", "TestName", 20)))
                        .isEqualTo("Updated using params: UpdateParams(id=myId, name=TestName, amount=20)")

                    it.assertThat(deleteUsingPath("MyId")).isEqualTo("Deleted using id: MyId")

                    it.assertThat(deleteUsingQuery("MyQuery")).isEqualTo("Deleted using query: MyQuery")
                }
            }
        }
    }

    @Test
    @Timeout(100)
    fun `operations on file upload using InputStream`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestFileUploadAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
        )

        val text = "some text for test"
        val text2 = "some other text for test with multi files"

        client.use {
            val connection = client.start()

            with(connection.proxy) {
                SoftAssertions.assertSoftly {
                    it.assertThat(
                        upload(text.byteInputStream())
                    ).isEqualTo(
                        generateChecksum(text.byteInputStream())
                    )

                    it.assertThat(
                        uploadWithName("someName", text.byteInputStream())
                    ).isEqualTo(
                        "someName, ${generateChecksum(text.byteInputStream())}"
                    )

                    it.assertThat(
                        multiInputStreamFileUpload(text.byteInputStream(), text2.byteInputStream())
                    ).isEqualTo(
                        "${generateChecksum(text.byteInputStream())}, ${generateChecksum(text2.byteInputStream())}"
                    )
                }
            }
        }
    }

    @Test
    @Timeout(100)
    fun `operations on file upload using HttpFileUpload`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestFileUploadAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
        )

        val text = "some text for test"
        val text2 = "some other text for test with multi files"

        client.use {
            val connection = client.start()

            with(connection.proxy) {
                SoftAssertions.assertSoftly {
                    it.assertThat(
                        fileUpload(HttpFileUpload(text.byteInputStream(), "", "", "SampleFile.txt", 123L))
                    ).isEqualTo(
                        generateChecksum(text.byteInputStream())
                    )

                    it.assertThat(
                        fileUploadWithQueryParam(
                            "tenant1",
                            HttpFileUpload(text.byteInputStream(), "", "", "SampleFile.txt", 0L)
                        )
                    ).isEqualTo(
                        "tenant1, ${generateChecksum(text.byteInputStream())}"
                    )

                    it.assertThat(
                        fileUploadWithPathParam(
                            "tenant1",
                            HttpFileUpload(text.byteInputStream(), "", "", "SampleFile.txt", 0L)
                        )
                    ).isEqualTo(
                        "tenant1, ${generateChecksum(text.byteInputStream())}"
                    )

                    it.assertThat(
                        fileUpload(
                            HttpFileUpload(text.byteInputStream(), "", "", "SampleFile1.txt", 123L),
                            HttpFileUpload(text2.byteInputStream(), "", "", "SampleFile2.txt", 123L),
                        )
                    ).isEqualTo(
                        "${generateChecksum(text.byteInputStream())}, ${generateChecksum(text2.byteInputStream())}"
                    )

                    // test client ability to send list of files
                    it.assertThat(
                        fileUploadObjectList(
                            listOf(
                                HttpFileUpload(text.byteInputStream(), "", "", "SampleFile1.txt", 123L),
                                HttpFileUpload(text2.byteInputStream(), "", "", "SampleFile2.txt", 123L)
                            )
                        )
                    ).isEqualTo(
                        "${generateChecksum(text.byteInputStream())}, ${generateChecksum(text2.byteInputStream())}"
                    )

                    it.assertThat(
                        fileUploadWithNameInAnnotation(
                            HttpFileUpload(text.byteInputStream(), "SampleFile.txt")
                        )
                    ).isEqualTo(
                        generateChecksum(text.byteInputStream())
                    )
                }
            }
        }
    }

    @Test
    @Timeout(100)
    fun `name in annotation method call`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
        )

        client.use {
            val connection = client.start()

            with(connection.proxy) {
                // Extra set of quotes will be fixed by https://r3-cev.atlassian.net/browse/CORE-4248
                assertThat(stringMethodWithNameInAnnotation("foo")).isEqualTo("Completed foo")
            }
        }
    }

    @Test
    @Timeout(100)
    fun `test api with nullable object return type that returns null`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
        )

        client.use {
            val connection = client.start()

            with(connection.proxy) {
                assertThat(apiReturningNullObject()).isNull()
            }
        }
    }

    @Test
    @Timeout(100)
    fun `test api with nullable String return type that returns null`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
        )

        client.use {
            val connection = client.start()

            with(connection.proxy) {
                val response = apiReturningNullString()
                assertThat(response).isEqualTo("null")
            }
        }
    }

    @Test
    @Timeout(100)
    fun `test api with object return type with nullable String inside returns that null string value`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
        )

        client.use {
            val connection = client.start()

            with(connection.proxy) {
                val response = apiReturningObjectWithNullableStringInside()
                assertThat(response).isNotNull
                assertThat(response.str).isNull()
            }
        }
    }

    @Test
    @Timeout(100)
    fun `optional query parameter call`() {
        val client = RestClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
        )

        client.use {
            val connection = client.start()

            with(connection.proxy) {
                assertThat(hello("name", 1)).isEqualTo("Hello 1 : name")
                assertThat(hello("name", null)).isEqualTo("Hello null : name")
                assertThat(hello2("world", "name")).isEqualTo("Hello queryParam: world, pathParam : name")
                assertThat(hello2(null, "name")).isEqualTo("Hello queryParam: null, pathParam : name")
            }
        }
    }

}
