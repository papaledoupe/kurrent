package org.lodenstone.kurrent.core.aggregate

import org.lodenstone.kurrent.core.eventstore.EventStore
import org.lodenstone.kurrent.core.util.loggerFor


abstract class AggregateService<A : Any>(private val eventStore: EventStore,
                                         private val snapshotStore: AggregateSnapshotStore<A>? = null) {

    companion object {
        val log = loggerFor<AggregateService<*>>()
    }

    abstract val aggregateType: String
    protected abstract val aggregateBuilder: Aggregate.Builder<A>

    protected abstract fun initializeState(): A

    fun handleCommand(aggregateId: String, aggregateVersion: Long, command: Command) {
        val aggregate = if (command is Initializing) {
            if (reconstructFromSnapshotAndOrEventStore(aggregateId) != null) {
                log.info("attempt to initialize existing $aggregateType $aggregateId")
                throw AggregateIdConflictException
            }
            log.debug("initializing new $aggregateType $aggregateId")
            initializeAggregate(aggregateId)
        } else {
            reconstructFromSnapshotAndOrEventStore(aggregateId)
                    ?: throw NoSuchAggregateException(aggregateType, aggregateId)
        }

        if (aggregate.info.version != aggregateVersion) {
            throw AggregateVersionConflictException
        }

        val events = aggregate.handle(command = command)

        eventStore.write(events.mapIndexed { ind, event ->
            TypedAggregateEvent(
                    aggregateInfo = AggregateInfo(
                            id = aggregateId,
                            version = aggregateVersion + 1 + ind,
                            type = aggregateType),
                    event = event)
        })
    }

    fun applyEvent(event: AggregateEvent<*>) {
        applyEventsAndStoreSnapshot(listOf(event))
    }

    fun loadLatestFromEventStore(aggregateId: String): Aggregate<A>? {
        val initialState = initializeState()
        val initialInfo = AggregateInfo(aggregateType, aggregateId, 0)
        val aggregate = aggregateBuilder.build(initialState, initialInfo)

        val events = eventStore.findAllEvents(aggregateType, aggregateId)
        if (events.isEmpty()) {
            return null
        }

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

    private fun reconstructFromSnapshotAndOrEventStore(aggregateId: String): Aggregate<A>? {
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

    private fun applyEventsAndStoreSnapshot(events: List<AggregateEvent<*>>, aggregate: Aggregate<A>) {
        if (events.isEmpty()) {
            log.debug("${aggregate.info} no new events")
            return
        }
        events.forEach { e -> aggregate.apply(e.event) }
        populateSnapshotStore(aggregate)
    }

    private fun initializeAggregate(aggregateId: String): Aggregate<A> {
        val initialInfo = AggregateInfo(aggregateType, aggregateId, 0)
        return aggregateBuilder.build(initializeState(), initialInfo)
    }

    private fun populateSnapshotStore(aggregate: Aggregate<A>) {
        snapshotStore?.put(aggregate)
        log.debug("${aggregate.info} stored snapshot")
    }
}

data class TypedAggregateEvent<T>(override val aggregateInfo: AggregateInfo,
                                  override val event: T) : AggregateEvent<T> where T : Event
