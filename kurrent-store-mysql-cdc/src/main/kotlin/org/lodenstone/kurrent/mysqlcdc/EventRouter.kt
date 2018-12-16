package org.lodenstone.kurrent.mysqlcdc

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.shyiko.mysql.binlog.event.WriteRowsEventData
import org.lodenstone.kurrent.core.aggregate.AggregateEvent
import org.lodenstone.kurrent.core.aggregate.AggregateInfo
import org.lodenstone.kurrent.core.aggregate.AggregateServiceRegistry
import org.lodenstone.kurrent.core.aggregate.Event
import org.lodenstone.kurrent.core.eventstore.EventRegistry
import org.lodenstone.kurrent.core.util.loggerFor
import kotlin.reflect.KClass
import com.github.shyiko.mysql.binlog.event.Event as LogEvent
import com.github.shyiko.mysql.binlog.event.EventData as LogEventData

interface EventRouter {
    fun route(event: LogEvent)
}

class DefaultEventRouter(private val objectMapper: ObjectMapper,
                         private val eventRegistry: EventRegistry,
                         private val aggregateServiceRegistry: AggregateServiceRegistry) : EventRouter {

    companion object {
        val logger = loggerFor<EventRouter>()
    }

    override fun route(event: LogEvent) {
        event.getData<WriteRowsEventData>().rows.map { data ->
            val eventType = data[3] as String
            eventRegistry.classForEventName<Event>(eventType)?.let { eventClass ->
                val aggregateEvent = LazyDeserializingEvent(
                    aggregateInfo = AggregateInfo(
                            type = data[0] as String,
                            id = data[1] as String,
                            version = (data[2] as Int).toLong()), // TODO cleanup
                    rawData = String(data[4] as ByteArray),
                    type = eventClass,
                    objectMapper = objectMapper)
                aggregateServiceRegistry.allServices().forEach { service ->
                    service.applyEvent(aggregateEvent)
                }
            } ?: logger.info("Unrecognised event type $eventType")
        }
    }
}

internal class LazyDeserializingEvent<T>(override val aggregateInfo: AggregateInfo,
                                         private val rawData: String,
                                         private val type: KClass<T>,
                                         private val objectMapper: ObjectMapper) : AggregateEvent<T> where T : Event {

    private var deserialized: T? = null

    override val event: T get() {
        if (deserialized == null) {
            deserialized = objectMapper.readValue(rawData, type.java)
        }
        return deserialized!!
    }
}