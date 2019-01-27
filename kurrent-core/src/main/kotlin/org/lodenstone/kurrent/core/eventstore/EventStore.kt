package org.lodenstone.kurrent.core.eventstore

import org.lodenstone.kurrent.core.aggregate.AggregateEvent
import org.lodenstone.kurrent.core.aggregate.AggregateInfo

interface EventStore {
    fun write(events: Collection<AggregateEvent<*>>)
    fun findLatestVersion(aggregateType: String, aggregateId: String): Long?
    fun findAllEvents(aggregateType: String, aggregateId: String): List<AggregateEvent<*>>
    fun findEventsAfterVersion(aggregateInfo: AggregateInfo): List<AggregateEvent<*>>
    fun findEventsUpToVersion(aggregateInfo: AggregateInfo): List<AggregateEvent<*>>
}

fun EventStore.write(vararg events: AggregateEvent<*>) = write(events.toList())

class InMemoryEventStore : EventStore {

    private data class AggregateTypeAndId(val type: String, val id: String)
    private val AggregateEvent<*>.mapStoreKey get() = AggregateTypeAndId(aggregateInfo.type, aggregateInfo.id)

    private val mapStore = mutableMapOf<AggregateTypeAndId, MutableList<AggregateEvent<*>>>()

    override fun write(events: Collection<AggregateEvent<*>>) {
        return events.forEach { event ->
            mapStore.getOrPut(event.mapStoreKey, ::mutableListOf).add(event)
        }
    }

    override fun findLatestVersion(aggregateType: String, aggregateId: String): Long? {
        return mapStore[AggregateTypeAndId(aggregateType, aggregateId)]
                ?.maxBy { it.aggregateInfo.version }
                ?.aggregateInfo
                ?.version
    }

    override fun findAllEvents(aggregateType: String, aggregateId: String): List<AggregateEvent<*>> {
        return mapStore[AggregateTypeAndId(aggregateType, aggregateId)] ?: emptyList()
    }

    override fun findEventsAfterVersion(aggregateInfo: AggregateInfo): List<AggregateEvent<*>> {
        return findAllEvents(aggregateInfo.type, aggregateInfo.id)
                .filter { it.aggregateInfo.version > aggregateInfo.version }
    }

    override fun findEventsUpToVersion(aggregateInfo: AggregateInfo): List<AggregateEvent<*>> {
        return findAllEvents(aggregateInfo.type, aggregateInfo.id)
                .filter { it.aggregateInfo.version <= aggregateInfo.version }
    }
}