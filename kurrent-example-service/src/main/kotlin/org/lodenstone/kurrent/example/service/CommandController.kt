package org.lodenstone.kurrent.example.service

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.readValue
import org.lodenstone.kurrent.core.aggregate.*
import org.lodenstone.kurrent.core.eventstore.CommandRegistry
import spark.Request
import spark.Response
import spark.Spark.exception
import spark.kotlin.get
import spark.kotlin.internalServerError
import spark.kotlin.post
import kotlin.Any
import kotlin.Int
import kotlin.Long
import kotlin.RuntimeException
import kotlin.String
import kotlin.let

const val jsonContentType = "application/json"

class CommandController(private val objectMapper: ObjectMapper,
                        private val commandRegistry: CommandRegistry,
                        private val aggregateServiceRegistry: AggregateServiceRegistry) {
    init {

        post("/command/:type/:id") {
            handleCommand(aggregateVersion = 0, request = request, response = response)
        }

        post("/command/:type/:id/version/:version", jsonContentType) {
            val version = request.params(":version").toLongOrNull() ?: throw BadRequestException
            handleCommand(aggregateVersion = version, request = request, response = response)
        }

        // For testing
        get("/command/:type/:id") {

            val service = aggregateServiceRegistry.serviceForType(request.params(":type"))
                    ?: throw NoSuchAggregateTypeException(request.params(":type"))

            val aggregate = service.loadFromSnapshotThenEventStoreFallback(request.params(":id"))
                    ?: throw NoSuchAggregateException(request.params(":type"), request.params(":id"))

            objectMapper.writeJsonResponse(response, obj = aggregate, status = 200)
        }

        exception(JsonParseException::class.java) { _, _, res ->
            objectMapper.writeJsonResponse(res, Error(message = "Invalid JSON"), 400)
        }

        exception(BadRequestException::class.java) { _, _, res ->
            objectMapper.writeJsonResponse(res, Error(message = "Invalid request"), 400)
        }

        exception(MissingKotlinParameterException::class.java) { e, _, res ->
            objectMapper.writeJsonResponse(res, Error(message = "Missing parameter ${e.parameter.name}"), 400)
        }

        exception(AggregateVersionConflictException::class.java) { _, _, res ->
            objectMapper.writeJsonResponse(res, Error(message = "Aggregate version conflict"), 409)
        }

        exception(AggregateIdConflictException::class.java) { _, _, res ->
            objectMapper.writeJsonResponse(res, Error(message = "Aggregate ID conflict"), 409)
        }

        exception(NoSuchAggregateException::class.java) { _, _, res ->
            objectMapper.writeJsonResponse(res, Error(message = "Aggregate does not exist"), 400)
        }

        exception(NoSuchAggregateTypeException::class.java) { _, _, res ->
            objectMapper.writeJsonResponse(res, Error(message = "Aggregate type does not exist"), 400)
        }

        exception(NoSuchCommandException::class.java) { _, _, res ->
            objectMapper.writeJsonResponse(res, Error(message = "Command not known"), 400)
        }

        exception(RejectedCommandException::class.java) { e, _, res ->
            objectMapper.writeJsonResponse(res, Error(message = "Command validation failed: ${e.reason}"), 400)
        }

        internalServerError {
            objectMapper.writeJsonResponse(response, Error(message = "Unhandled error"))
        }
    }

    private fun handleCommand(aggregateVersion: Long, request: Request, response: Response): String {
        val aggregateInfo = AggregateInfo(id = request.params(":id"),
                version = aggregateVersion,
                type = request.params(":type"))

        val commandRequest = objectMapper.readValue<CommandRequest>(request.bodyAsBytes())

        val service = aggregateServiceRegistry.serviceForType(aggregateInfo.type)
                ?: throw NoSuchAggregateTypeException(aggregateInfo.type)
        val commandClass = commandRegistry.classForCommandName<Command>(commandRequest.command)
                ?: throw NoSuchCommandException(commandRequest.command)
        val command = objectMapper.readValue(commandRequest.data, commandClass.java)

        service.handleCommand(aggregateInfo.id, aggregateInfo.version, command)

        return objectMapper.writeJsonResponse(response, status = 202)
    }
}

private class CommandRequest(
        val command: String,
        data: JsonNode) {
    val data = data.toString()
    override fun toString() = "[ command='$command', data='$data' ]"
}

object BadRequestException : RuntimeException()

private class EmptyResponse
private data class Error(val message: String)

private fun ObjectMapper.writeJsonResponse(response: Response, obj: Any? = EmptyResponse(), status: Int? = null): String {
    val responseVal = writeValueAsString(obj)
    status?.let { response.status(it) }
    response.type(jsonContentType)
    response.body(responseVal)
    return responseVal
}