package net.corda.rest.server.impl.utils

import net.corda.rest.server.impl.apigen.models.Endpoint
import net.corda.rest.server.impl.apigen.models.EndpointMethod
import net.corda.rest.server.impl.apigen.models.EndpointParameter
import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.models.InvocationMethod
import net.corda.rest.server.impl.apigen.models.ParameterType
import net.corda.rest.server.impl.apigen.models.Resource
import net.corda.rest.server.impl.apigen.models.ResponseBody
import net.corda.rest.test.TestHealthCheckAPI
import net.corda.rest.test.TestHealthCheckAPIImpl
import kotlin.reflect.jvm.javaMethod

internal fun getHealthCheckApiTestResource(): Resource {

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