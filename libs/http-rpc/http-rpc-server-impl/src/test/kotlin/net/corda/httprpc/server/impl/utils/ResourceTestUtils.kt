package net.corda.httprpc.server.impl.utils

import net.corda.httprpc.server.apigen.test.TestJavaPrimitivesRPCopsImpl
import net.corda.httprpc.server.apigen.test.TestJavaPrimitivesRpcOps
import net.corda.httprpc.server.impl.apigen.models.Endpoint
import net.corda.httprpc.server.impl.apigen.models.EndpointMethod
import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.models.InvocationMethod
import net.corda.httprpc.server.impl.apigen.models.ParameterType
import net.corda.httprpc.server.impl.apigen.models.Resource
import net.corda.httprpc.server.impl.apigen.models.ResponseBody
import net.corda.httprpc.test.TestHealthCheckAPI
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import kotlin.reflect.jvm.javaMethod

fun getHealthCheckApiTestResource(): Resource {

  val endpointVoid = Endpoint(
    method = EndpointMethod.GET,
    title = "Void",
    description = "Void endpoint",
    path = "void",
    parameters = listOf(),
    responseBody = ResponseBody(description = "", type = Void.TYPE),
    invocationMethod = InvocationMethod(method = TestHealthCheckAPI::voidResponse.javaMethod!!, instance = TestHealthCheckAPIImpl())
  )
  val endpointSanity = Endpoint(
    method = EndpointMethod.GET,
    title = "Sanity",
    description = "Sanity endpoint",
    path = "sanity",
    parameters = listOf(),
    responseBody = ResponseBody(description = "", type = String::class.java),
    invocationMethod = InvocationMethod(method = TestHealthCheckAPI::void.javaMethod!!, instance = TestHealthCheckAPIImpl())
  )

  val endpointHello2 = Endpoint(
    method = EndpointMethod.GET,
    title = "Hello2",
    description = "Hello endpoint",
    path = "hello2/{name}",
    parameters = listOf(
      EndpointParameter(
        id = "queryParam",
        name = "id",
        description = "id",
        required = false,
        classType = String::class.java,
        type = ParameterType.QUERY,
        default = null
      ),
      EndpointParameter(
        id = "pathParam",
        name = "name",
        description = "The name",
        required = true,
        classType = String::class.java,
        type = ParameterType.PATH,
        default = null
      )

    ),
    responseBody = ResponseBody(description = "", type = String::class.java),
    invocationMethod = InvocationMethod(method = TestHealthCheckAPI::hello2.javaMethod!!, instance = TestHealthCheckAPIImpl())
  )

  val endpointHello = Endpoint(

    method = EndpointMethod.GET,
    title = "Hello",
    description = "Hello endpoint",
    path = "hello/{name}",
    parameters = listOf(
      EndpointParameter(
        id = "pathParam",
        name = "name",
        description = "The name",
        required = true,
        classType = String::class.java,
        type = ParameterType.PATH,
        default = null
      ),
      EndpointParameter(
        id = "param",
        name = "id",
        description = "id",
        required = false,
        classType = Integer::class.java,
        type = ParameterType.QUERY,
        default = null
      )
    ),
    responseBody = ResponseBody(description = "", type = String::class.java),
    invocationMethod = InvocationMethod(method = TestHealthCheckAPI::hello.javaMethod!!, instance = TestHealthCheckAPIImpl())
  )

  val endpointPing = Endpoint(
    method = EndpointMethod.POST,
    title = "ping",
    description = "",
    path = "ping",
    parameters = listOf(
      EndpointParameter(
        id = "data",
        description = "Data",
        name = "data",
        required = false,
        classType = TestHealthCheckAPI.PingPongData::class.java,
        type = ParameterType.BODY,
        default = null
      )
    ),
    responseBody = ResponseBody(description = "", type = String::class.java),
    invocationMethod = InvocationMethod(method = TestHealthCheckAPI::ping.javaMethod!!, instance = TestHealthCheckAPIImpl())
  )

  val endpointplusOne = Endpoint(
    method = EndpointMethod.GET,
    title = "plusOne",
    description = "",
    path = "plusone",
    parameters = listOf(
      EndpointParameter(
        id = "data",
        description = "",
        name = "data",
        required = true,
        classType = List::class.java,
        type = ParameterType.QUERY,
        default = null,
        parameterizedTypes = listOf(GenericParameterizedType(String::class.java))
      )
    ),
    responseBody = ResponseBody(
      description = "Increased by one",
      type = List::class.java,
      parameterizedTypes = listOf(GenericParameterizedType(java.lang.Double::class.java))
    ),
    invocationMethod = InvocationMethod(method = TestHealthCheckAPI::plusOne.javaMethod!!, instance = TestHealthCheckAPIImpl())
  )

  val endpointPlus = Endpoint(
    method = EndpointMethod.POST,
    title = "AddOne",
    description = "Add One",
    path = "plusone/{number}",
    parameters = listOf(
      EndpointParameter(
        id = "number",
        description = "",
        name = "number",
        required = true,
        classType = Long::class.java,
        type = ParameterType.PATH,
        default = null
      )
    ),
    responseBody = ResponseBody(description = "", type = Long::class.java),
    invocationMethod = InvocationMethod(method = TestHealthCheckAPI::plus.javaMethod!!, instance = TestHealthCheckAPIImpl())
  )

  val endpointBodyPlayground = Endpoint(
    method = EndpointMethod.POST,
    title = "bodyPlayground",
    description = "",
    path = "bodyplayground",
    parameters = listOf(
      EndpointParameter(
        id = "s1",
        description = "",
        name = "s1",
        required = true,
        classType = String::class.java,
        type = ParameterType.BODY,
        default = null
      ),
      EndpointParameter(
        id = "s2",
        description = "",
        name = "s2",
        required = false,
        classType = String::class.java,
        type = ParameterType.BODY,
        default = null
      )
    ),
    responseBody = ResponseBody(description = "", type = String::class.java),
    invocationMethod = InvocationMethod(method = TestHealthCheckAPI::bodyPlayground.javaMethod!!, instance = TestHealthCheckAPIImpl())
  )

  val endpointTimeCall = Endpoint(
    method = EndpointMethod.POST,
    title = "timeCall",
    description = "",
    path = "timecall",
    parameters = listOf(
      EndpointParameter(
        id = "time",
        description = "",
        name = "time",
        required = true,
        classType = TestHealthCheckAPI.TimeCallDto::class.java,
        type = ParameterType.BODY,
        default = null
      )
    ),
    responseBody = ResponseBody(description = "", type = String::class.java),
    invocationMethod = InvocationMethod(method = TestHealthCheckAPI::timeCall.javaMethod!!, instance = TestHealthCheckAPIImpl())
  )


  return Resource(
    "HealthCheckAPI", "Health Check", "health/",
    setOf(
      endpointVoid,
      endpointSanity,
      endpointHello2,
      endpointHello,
      endpointPing,
      endpointplusOne,
      endpointPlus,
      endpointBodyPlayground,
      endpointTimeCall
    )
  )
}

fun getTestJavaPrimitivesRPCopsTestResource(): Resource {

  val endPointNegateInt = Endpoint(
    method = EndpointMethod.POST,
    title = "Negate Integer",
    description = "Negate an Integer",
    path = "negateInteger",
    parameters = listOf(
      EndpointParameter(
        id = "number",
        description = "Int",
        name = "number",
        required = false,
        classType = Integer::class.java,
        type = ParameterType.BODY,
        default = null
      )
    ),
    responseBody = ResponseBody(description = "", type = Integer::class.java),
    invocationMethod = InvocationMethod(
      method = TestJavaPrimitivesRpcOps::negateInt.javaMethod!!,
      instance = TestJavaPrimitivesRPCopsImpl()
    )
  )

  val endPointNegateLong = Endpoint(
    method = EndpointMethod.GET,
    title = "Negate Long",
    description = "Negate a Long value",
    path = "negate_long",
    parameters = listOf(
      EndpointParameter(
        id = "number",
        description = "",
        name = "number",
        required = true,
        classType = java.lang.Long::class.java,
        type = ParameterType.QUERY,
        default = null
      )
    ),
    responseBody = ResponseBody(description = "", type = java.lang.Long::class.java),
    invocationMethod = InvocationMethod(
      method = TestJavaPrimitivesRpcOps::negateLong.javaMethod!!,
      instance = TestJavaPrimitivesRPCopsImpl()
    )
  )

  val endPointNegateBoolean = Endpoint(
    method = EndpointMethod.GET,
    title = "Negate Boolean",
    description = "Negate a Boolean value",
    path = "negate_boolean",
    parameters = listOf(
      EndpointParameter(
        id = "bool",
        description = "",
        name = "bool",
        required = true,
        classType = java.lang.Boolean::class.java,
        type = ParameterType.QUERY,
        default = null
      )
    ),
    responseBody = ResponseBody(description = "", type = java.lang.Boolean::class.java),
    invocationMethod = InvocationMethod(
      method = TestJavaPrimitivesRpcOps::negateBoolean.javaMethod!!,
      instance = TestJavaPrimitivesRPCopsImpl()
    )
  )

  val endPointReverse = Endpoint(
    method = EndpointMethod.GET,
    title = "Reverse Text",
    description = "Reverse a text",
    path = "reverse/{text}",
    parameters = listOf(
      EndpointParameter(
        id = "text",
        description = "The text to reverse",
        name = "text",
        required = true,
        classType = java.lang.String::class.java,
        type = ParameterType.PATH,
        default = null
      )
    ),
    responseBody = ResponseBody(description = "", type = java.lang.String::class.java),
    invocationMethod = InvocationMethod(
      method = TestJavaPrimitivesRpcOps::reverse.javaMethod!!,
      instance = TestJavaPrimitivesRPCopsImpl()
    )
  )

  return Resource(
    "API", "Java Test", "java",
    setOf(endPointNegateInt, endPointNegateLong, endPointNegateBoolean, endPointReverse)
  )
}