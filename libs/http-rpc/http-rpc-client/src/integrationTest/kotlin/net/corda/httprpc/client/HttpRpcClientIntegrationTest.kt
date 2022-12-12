package net.corda.httprpc.client

import net.corda.httprpc.client.config.HttpRpcClientConfig
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.HttpRpcServerImpl
import net.corda.httprpc.test.CalendarRPCOps
import net.corda.httprpc.test.CalendarRPCOpsImpl
import net.corda.httprpc.test.CustomSerializationAPI
import net.corda.httprpc.test.CustomSerializationAPIImpl
import net.corda.httprpc.test.CustomString
import net.corda.httprpc.test.NumberSequencesRPCOps
import net.corda.httprpc.test.NumberSequencesRPCOpsImpl
import net.corda.httprpc.test.NumberTypeEnum
import net.corda.httprpc.test.TestEntityRpcOps
import net.corda.httprpc.test.TestEntityRpcOpsImpl
import net.corda.httprpc.test.TestHealthCheckAPI
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.test.util.eventually
import net.corda.utilities.NetworkHostAndPort
import net.corda.v5.base.util.seconds
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
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.test.TestFileUploadAPI
import net.corda.httprpc.test.TestFileUploadImpl
import net.corda.httprpc.test.utils.ChecksumUtil.generateChecksum
import net.corda.httprpc.test.utils.multipartDir

internal class HttpRpcClientIntegrationTest : HttpRpcIntegrationTestBase() {
    companion object {

        @BeforeAll
        @JvmStatic
        @Suppress("Unused")
        fun setUpBeforeClass() {
            val httpRpcSettings = HttpRpcSettings(
                NetworkHostAndPort("localhost", 0),
                context,
                null,
                null,
                HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
                20000L
            )
            server = HttpRpcServerImpl(
                listOf(
                    TestHealthCheckAPIImpl(),
                    CustomSerializationAPIImpl(),
                    NumberSequencesRPCOpsImpl(),
                    CalendarRPCOpsImpl(),
                    TestEntityRpcOpsImpl(),
                    TestFileUploadImpl()
                ),
                ::securityManager,
                httpRpcSettings,
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
            healthCheckInterval = 500
        )

        val connected = AtomicBoolean()

        val listener = object : HttpRpcConnectionListener<TestHealthCheckAPI> {
            override fun onConnect(context: HttpRpcConnectionListener.HttpRpcConnectionContext<TestHealthCheckAPI>) {
                connected.set(true)
            }

            override fun onDisconnect(context: HttpRpcConnectionListener.HttpRpcConnectionContext<TestHealthCheckAPI>) {
                connected.set(false)
            }

            override fun onPermanentFailure(context: HttpRpcConnectionListener.HttpRpcConnectionContext<TestHealthCheckAPI>) {
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            CustomSerializationAPI::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            NumberSequencesRPCOps::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            CalendarRPCOps::class.java,
            HttpRpcClientConfig()
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
                    assertEquals(CalendarRPCOps.CalendarDay(DayOfWeek.WEDNESDAY, "2020-01-01"), values.first())
                    assertEquals(CalendarRPCOps.CalendarDay(DayOfWeek.THURSDAY, "2020-04-09"), values.last())
                    assertFalse(this.isLastResult)
                    // no commit
                }

                with(cursor.poll(300, 100.seconds)) {
                    assertEquals(300, values.size)
                    assertEquals(CalendarRPCOps.CalendarDay(DayOfWeek.WEDNESDAY, "2020-01-01"), values.first())
                    assertEquals(CalendarRPCOps.CalendarDay(DayOfWeek.MONDAY, "2020-10-26"), values.last())
                    assertFalse(this.isLastResult)
                    cursor.commit(this)
                }

                with(cursor.poll(100, 100.seconds)) {
                    assertEquals(66, values.size)
                    assertEquals(CalendarRPCOps.CalendarDay(DayOfWeek.TUESDAY, "2020-10-27"), values.first())
                    assertEquals(CalendarRPCOps.CalendarDay(DayOfWeek.THURSDAY, "2020-12-31"), values.last())
                    assertTrue(this.isLastResult)
                    cursor.commit(this)
                }
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client against server with less than rpc version since but valid version for the resource fails only on the unsupported call`() {
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestEntityRpcOps::class.java,
            HttpRpcClientConfig()
                .enableSSL(false)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password)),
        )

        client.use {
            val connection = client.start()

            with(connection.proxy) {

                SoftAssertions.assertSoftly {
                    it.assertThat(create(TestEntityRpcOps.CreationParams("TestName", 20)))
                        .isEqualTo("Created using: CreationParams(name=TestName, amount=20)")

                    it.assertThat(getUsingPath("MyId")).isEqualTo("Retrieved using id: MyId")

                    it.assertThat(getUsingQuery("MyQuery")).isEqualTo("Retrieved using query: MyQuery")

                    it.assertThat(update(TestEntityRpcOps.UpdateParams("myId", "TestName", 20)))
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestFileUploadAPI::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestFileUploadAPI::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
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
        val client = HttpRpcClient(
            baseAddress = "http://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
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
