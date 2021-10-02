package net.corda.httprpc.endpoints.impl

import io.javalin.apibuilder.ApiBuilder
import net.corda.v5.httprpc.api.Controller
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.apibuilder.ApiBuilder.delete
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.ContentType
import io.javalin.plugin.openapi.annotations.OpenApi
import io.javalin.plugin.openapi.annotations.OpenApiContent
import io.javalin.plugin.openapi.annotations.OpenApiParam
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody
import io.javalin.plugin.openapi.annotations.OpenApiResponse
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@Component(service = [Controller::class])
class StubControllerImpl : Controller {

    private companion object {
        private val log = contextLogger()
    }

    override fun register() {
        path("stubs") {
            get(::all)
            post(::post)
//            path("{id}") {
//                get(::get)
//                put(::put)
//                delete(::delete)
//            }
            // this is the javalin 3 syntax, which will change when we move to v4
                get("/:id", ::get)
                put("/:id", ::put)
                delete("/:id", ::delete)
        }
    }

    private fun all(ctx: Context) {
        println(ctx)
        log.info("ALL")
    }

    @OpenApi(
        summary = "Get some json with the passed in id",
        pathParams = [OpenApiParam(name = "id", required = true, type = String::class, description = "The id to get some json with")],
        operationId = "get",
        tags = ["Stub API"],
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(SomeJson::class, type = ContentType.JSON)]),
            OpenApiResponse(status = "404"),
            OpenApiResponse(status = "500")
        ]
    )
    private fun get(ctx: Context) {
        val id = ctx.pathParam("id")
        log.info("GET - id")
        ctx.status(200)
        ctx.json(SomeJson(id, 2))
    }

    @OpenApi(
        summary = "Save some json",
        requestBody = OpenApiRequestBody(content = [OpenApiContent(SomeJson::class, type = ContentType.JSON)], description = "The json to save"),
        operationId = "post",
        tags = ["Stub API"],
        responses = [
            OpenApiResponse(status = "201", content = [OpenApiContent(SomeJson::class, type = ContentType.JSON)]),
            OpenApiResponse(status = "500")
        ]
    )
    private fun post(ctx: Context) {
        println(ctx)
        val json = ctx.bodyAsClass(SomeJson::class.java)
        log.info("POST - $json")
        ctx.status(201)
        ctx.json(json)
    }

    private fun put(ctx: Context) {
        val id = ctx.pathParam("id")
        log.info("PUT - $id")
        throw IllegalStateException("This is an exception and I am breaking")
    }

    private fun delete(ctx: Context) {
        val id = ctx.pathParam("id")
        log.info("DELETE - $id")
        ctx.result(id)
    }

    class SomeJson(val a: String, val b: Int)
}