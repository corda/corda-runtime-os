//package net.corda.web.server
//
//
//import io.javalin.Javalin
//import net.corda.v5.base.exceptions.CordaRuntimeException
//import org.junit.jupiter.api.AfterEach
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import org.junit.jupiter.api.assertThrows
//import org.mockito.Mockito
//import org.mockito.kotlin.verify
//
//class JavalinServerTest {
//
//    private lateinit var javalinServer: JavalinServer
//    private lateinit var javalinMock: Javalin
//
//    @BeforeEach
//    fun setUp() {
//        javalinMock = Mockito.mock(Javalin::class.java)
//        javalinServer = Mockito.spy(JavalinServer())
//
//        val serverField = JavalinServer::class.java.getDeclaredField("server")
//        serverField.isAccessible = true
//        serverField.set(javalinServer, javalinMock)
//    }
//
//    @AfterEach
//    fun down() {
//
//    }
//
//    @Test
//    fun `starting the server should call start on Javalin`() {
//        javalinServer.start(8080)
//        verify(javalinMock).start(8080)
//    }
//
//    @Test
//    fun `stopping the server should call stop on Javalin`() {
//        javalinServer.stop()
//        verify(javalinMock).stop()
//    }
//
//    @Test
//    fun `starting an already started server should throw exception`() {
//        assertThrows<CordaRuntimeException> {
//            javalinServer.start(8080)
//        }
//    }

//    @Test
//    fun `registering a null or invalid handler should throw exception`() {
//        val exception = assertThrows<CordaRuntimeException> {
//            javalinServer.registerHandler(HTTPMethod.GET, "/endpoint", ())
//        }
//        assert(exception.message!!.contains("The Javalin webserver has not been initialized"))
//    }

    // ... you can continue adding more tests based on different scenarios
//}