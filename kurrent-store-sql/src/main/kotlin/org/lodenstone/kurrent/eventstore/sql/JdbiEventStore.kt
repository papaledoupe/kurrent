package org.lodenstone.kurrent.eventstore.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.core.statement.UnableToExecuteStatementException
import org.jdbi.v3.sqlobject.SqlObject
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.attach
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.lodenstone.kurrent.core.aggregate.AggregateEvent
import org.lodenstone.kurrent.core.aggregate.AggregateInfo
import org.lodenstone.kurrent.core.aggregate.AggregateVersionConflictException
import org.lodenstone.kurrent.core.aggregate.Event
import org.lodenstone.kurrent.core.eventstore.EventRegistry
import org.lodenstone.kurrent.core.eventstore.EventStore
import org.lodenstone.kurrent.core.util.loggerFor
import java.sql.ResultSet
import java.sql.SQLIntegrityConstraintViolationException
import kotlin.reflect.KClass

class JdbiEventStore(private val repository: EventRepository,
                     private val eventRegistry: EventRegistry,
                     private val objectMapper: ObjectMapper = jacksonObjectMapper()) : EventStore {

    constructor(jdbi: Jdbi, eventRegistry: EventRegistry, objectMapper: ObjectMapper = jacksonObjectMapper())
            : this(jdbi.configure().open().attach<EventRepository>(), eventRegistry, objectMapper)

    companion object {
        private val logger = loggerFor<JdbiEventStore>()
    }

    override fun write(events: Collection<AggregateEvent<*>>) = repository.transactionally {
        events.forEach { event ->
            val eventName = eventRegistry.eventNameForClass(event.event::class)
                    ?: throw IllegalArgumentException("Unrecognised event: ${event.event::class.simpleName}")
            val eventData = EventData(
                    aggregateInfo = event.aggregateInfo,
                    eventName = eventName,
                    event = objectMapper.writeValueAsString(event.event))
            try {
                repository.insert(eventData)
                logger.info("Wrote event: $eventData")
            } catch (e: UnableToExecuteStatementException) {
                if (e.cause is SQLIntegrityConstraintViolationException) {
                    throw AggregateVersionConflictException("version ${eventData.aggregateInfo.version} already exists")
                } else {
                    throw e
                }
            }
        }
    }

    override fun findLatestVersion(aggregateType: String, aggregateId: String): Long? = repository.findLatestVersion(aggregateType, aggregateId)

    override fun findAllEvents(aggregateType: String, aggregateId: String): List<AggregateEvent<*>> {
        return repository.findEvents(aggregateType, aggregateId).map(this::rawEventDataToTyped)
    }

    override fun findEventsAfterVersion(aggregateInfo: AggregateInfo): List<AggregateEvent<*>> {
        return repository.findEventsAfterVersion(
                aggregateVersion = aggregateInfo.version,
                aggregateId = aggregateInfo.id,
                aggregateType =  aggregateInfo.type)
                .map(this::rawEventDataToTyped)
    }

    override fun findEventsUpToVersion(aggregateInfo: AggregateInfo): List<AggregateEvent<*>> {
        return repository.findEventsUpToVersion(
                aggregateVersion = aggregateInfo.version,
                aggregateId = aggregateInfo.id,
                aggregateType =  aggregateInfo.type)
                .map(this::rawEventDataToTyped)
    }

    private fun rawEventDataToTyped(eventData: EventData): LazyDeserializingEvent<*> {
        val clazz = eventRegistry.classForEventName<Event>(eventData.eventName)
                ?: throw IllegalArgumentException("Unrecognised event: ${eventData.event}")
        return LazyDeserializingEvent(
                aggregateInfo = eventData.aggregateInfo,
                rawData = eventData.event,
                type = clazz,
                objectMapper = objectMapper)
    }
}

class LazyDeserializingEvent<T>(override val aggregateInfo: AggregateInfo,
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

data class EventData(val aggregateInfo: AggregateInfo,
                     val event: String,
                     val eventName: String)

interface EventRepository : SqlObject {
    @SqlUpdate("""
        INSERT INTO events (
            aggregate_type,
            aggregate_id,
            aggregate_version,
            event,
            data
        ) VALUES (
            :event.aggregateInfo.type,
            :event.aggregateInfo.id,
            :event.aggregateInfo.version,
            :event.eventName,
            :event.event
        )
    """) fun insert(event: EventData)

    @RegisterRowMapper(RawEventMapper::class)
    @SqlQuery("""
        SELECT
            aggregate_type,
            aggregate_id,
            aggregate_version,
            event,
            data
        FROM events
            WHERE aggregate_type = :aggregateType
            AND   aggregate_id   = :aggregateId
        ORDER BY aggregate_version ASC
    """) fun findEvents(aggregateType: String, aggregateId: String): List<EventData>

    @RegisterRowMapper(RawEventMapper::class)
    @SqlQuery("""
        SELECT
            aggregate_type,
            aggregate_id,
            aggregate_version,
            event,
            data
        FROM events
            WHERE aggregate_type    =  :aggregateType
            AND   aggregate_id      =  :aggregateId
            AND   aggregate_version <= :aggregateVersion
        ORDER BY aggregate_version ASC
    """) fun findEventsUpToVersion(aggregateType: String, aggregateId: String, aggregateVersion: Long): List<EventData>

    @RegisterRowMapper(RawEventMapper::class)
    @SqlQuery("""
        SELECT
            aggregate_type,
            aggregate_id,
            aggregate_version,
            event,
            data
        FROM events
            WHERE aggregate_type    = :aggregateType
            AND   aggregate_id      = :aggregateId
            AND   aggregate_version > :aggregateVersion
        ORDER BY aggregate_version ASC
    """) fun findEventsAfterVersion(aggregateType: String, aggregateId: String, aggregateVersion: Long): List<EventData>

    @SqlQuery("""
        SELECT
            MAX(aggregate_version)
        FROM events
            WHERE aggregate_type = :aggregateType
            AND   aggregate_id   = :aggregateId
    """) fun findLatestVersion(aggregateType: String, aggregateId: String): Long?

    class RawEventMapper : RowMapper<EventData> {
        override fun map(rs: ResultSet, ctx: StatementContext) = EventData(
                aggregateInfo = AggregateInfo(
                        type = rs.getString("aggregate_type"),
                        id = rs.getString("aggregate_id"),
                        version = rs.getLong("aggregate_version")),
                eventName = rs.getString("event"),
                event = rs.getString("data")
        )
    }
}

private fun Jdbi.configure(): Jdbi {
    installPlugin(SqlObjectPlugin())
    installPlugin(KotlinPlugin())
    installPlugin(KotlinSqlObjectPlugin())
    return this
}

private fun EventRepository.transactionally(action: (Handle) -> Unit) = handle.useTransaction<Nothing>(action)