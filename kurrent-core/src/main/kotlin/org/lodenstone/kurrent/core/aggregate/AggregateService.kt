package org.lodenstone.kurrent.core.aggregate

import org.lodenstone.kurrent.core.eventstore.EventStore
import org.lodenstone.kurrent.core.util.loggerFor


abstract class AggregateService<TData : Any>(
        private val eventStore: EventStore,
        private val snapshotStore: AggregateSnapshotStore<TData>? = null
) {

    companion object {
        val log = loggerFor<AggregateService<*>>()
    }

    abstract val aggregateType: String

    protected abstract val aggregateBuilder: Aggregate.Builder<TData>

    fun handleCommand(aggregateId: String, aggregateVersion: Long?, command: Command) {
        val aggregate = if (command is Initialize) {
            if (reconstructFromSnapshotAndOrEventStore(aggregateId) != null) {
                log.info("attempt to initialize existing $aggregateType $aggregateId")
                throw AggregateIdConflictException
            }
            log.debug("initializing new $aggregateType $aggregateId")
            aggregateBuilder.build(null, AggregateInfo(aggregateType, aggregateId, 0))
        } else {
            reconstructFromSnapshotAndOrEventStore(aggregateId)
                    ?: throw NoSuchAggregateException(aggregateType, aggregateId)
        }

        aggregateVersion?.let {
            if (aggregate.info.version != it) {
                throw AggregateVersionConflictException
            }
        }

        val events = aggregate.handle(command = command)

        eventStore.write(events.mapIndexed { ind, event ->
            val aggregateInfo = AggregateInfo(
                    id = aggregateId,
                    version = aggregate.info.version + 1 + ind,
                    type = aggregateType)
            log.debug("$aggregateInfo will write event $event")
            TypedAggregateEvent(
                    aggregateInfo = aggregateInfo,
                    event = event)
        })
    }

    fun applyEvent(event: AggregateEvent<*>) {
        applyEventsAndStoreSnapshot(listOf(event))
    }

    fun loadLatestFromEventStore(aggregateId: String): Aggregate<TData>? {
        val events = eventStore.findAllEvents(aggregateType, aggregateId)
        if (events.isEmpty()) {
            return null
        }
        val initialInfo = AggregateInfo(aggregateType, aggregateId, 0)
        val aggregate = aggregateBuilder.build(null, initialInfo)

        log.debug("${aggregate.info} rebuilding from event store")
        applyEventsAndStoreSnapshot(events, aggregate)
        log.debug("${aggregate.info} rebuilt from event store")
        return aggregate
    }

    fun loadLatest(aggregateId: String) = reconstructFromSnapshotAndOrEventStore(aggregateId)

    private fun applyEventsAndStoreSnapshot(events: List<AggregateEvent<*>>) {
        val relevantEvents = events.filter { it.aggregateInfo.type == aggregateType }
        if (relevantEvents.isEmpty()) {
            return
        }
        val id = events.first().aggregateInfo.id
        val aggregate = reconstructFromSnapshotAndOrEventStore(id) ?: throw NoSuchAggregateException(aggregateType, id)
        applyEventsAndStoreSnapshot(relevantEvents, aggregate)
    }

    private fun reconstructFromSnapshotAndOrEventStore(aggregateId: String): Aggregate<TData>? {
        val snapshot = snapshotStore?.getLatest(aggregateId)
        if (snapshot == null) {
            log.debug("No snapshot for $aggregateType $aggregateId")
            return loadLatestFromEventStore(aggregateId)
        }
        val (snapshottedData, snapshottedVerison) = snapshot
        val snapshottedInfo = AggregateInfo(aggregateType, aggregateId, snapshottedVerison)
        log.debug("$snapshottedInfo snapshot found")
        val aggregate = aggregateBuilder.build(snapshottedData, snapshottedInfo)
        val moreRecentEvents = eventStore.findEventsAfterVersion(snapshottedInfo)
        applyEventsAndStoreSnapshot(moreRecentEvents, aggregate)
        return aggregate
    }

    private fun applyEventsAndStoreSnapshot(events: List<AggregateEvent<*>>, aggregate: Aggregate<TData>) {
        if (events.isEmpty()) {
            log.debug("${aggregate.info} no new events")
            return
        }
        events.forEach { e -> aggregate.apply(e.event) }
        populateSnapshotStore(aggregate)
    }

    private fun populateSnapshotStore(aggregate: Aggregate<TData>) {
        val data = aggregate.data ?: throw IllegalArgumentException("cannot snapshot uninitialized aggregate")
        snapshotStore?.put(aggregate.info, data)
        log.debug("${aggregate.info} stored snapshot")
    }
}

data class TypedAggregateEvent<T>(override val aggregateInfo: AggregateInfo,
                                  override val event: T) : AggregateEvent<T> where T : Event
