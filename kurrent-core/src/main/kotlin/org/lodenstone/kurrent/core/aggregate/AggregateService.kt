package org.lodenstone.kurrent.core.aggregate

import org.lodenstone.kurrent.core.eventstore.EventStoreClient
import org.lodenstone.kurrent.core.util.loggerFor


abstract class AggregateService<A>(private val eventStoreClient: EventStoreClient) where A : Any {

    companion object {
        val log = loggerFor<AggregateService<*>>()
    }

    abstract val aggregateType: String
    abstract val snapshotStore: AggregateSnapshotStore<A>?
    protected abstract val aggregateBuilder: Aggregate.Builder<A>

    protected abstract fun initializeState(): A

    fun handleCommand(aggregateId: String, aggregateVersion: Long, command: Command) {
        val aggregate = if (command is Initializing) {
            if (loadFromSnapshotThenEventStoreFallback(aggregateId) != null) {
                throw AggregateIdConflictException
            }
            initializeAggregate(aggregateId)
        } else {
            loadFromSnapshotThenEventStoreFallback(aggregateId)
                    ?: throw NoSuchAggregateException(aggregateType, aggregateId)
        }

        val events = aggregate.handle(commandVersion = aggregateVersion, command = command)

        eventStoreClient.write(events.mapIndexed { ind, event ->
            TypedAggregateEvent(
                    aggregateInfo = AggregateInfo(
                            id = aggregateId,
                            version = aggregateVersion + 1 + ind,
                            type = aggregateType),
                    event = event)
        })
    }

    fun applyEvent(event: AggregateEvent<*>) {
        applyEvents(listOf(event))
    }

    fun applyEvents(events: List<AggregateEvent<*>>) {
        val relevantEvents = events.filter { it.aggregateInfo.type == aggregateType }
        if (relevantEvents.isEmpty()) {
            return
        }
        val id = events.first().aggregateInfo.id
        val aggregate = loadFromSnapshotThenEventStoreFallback(id) ?: throw NoSuchAggregateException(aggregateType, id)
        applyEvents(relevantEvents, aggregate)
    }

    fun loadFromSnapshotThenEventStoreFallback(aggregateId: String) =
            // Potential optimization - take current snapshot state and apply only necessary events; currently rebuilds
            // entirely from event stream
            loadLatestFromSnapshotStore(aggregateId) ?: loadLatestFromEventStore(aggregateId)

    fun loadLatestFromSnapshotStore(aggregateId: String): Aggregate<A>? {
        val latestVersion = eventStoreClient.findLatestVersion(aggregateType, aggregateId) ?: return null
        return snapshotStore?.get(aggregateId, latestVersion)?.let { data ->
            aggregateBuilder.build(data, AggregateInfo(aggregateType, aggregateId, latestVersion))
        }
    }

    fun loadLatestFromEventStore(aggregateId: String): Aggregate<A>? {

        val initialState = initializeState()
        val initialInfo = AggregateInfo(aggregateType, aggregateId, 0)
        val aggregate = aggregateBuilder.build(initialState, initialInfo)

        val events = eventStoreClient.findAllEvents(aggregateType, aggregateId)
        if (events.isEmpty()) {
            return null
        }

        log.debug("${aggregate.info} rebuilding from event store")
        events.forEach { e -> aggregate.apply(e.event) }
        log.debug("${aggregate.info} rebuilt from event store")
        populateSnapshotStore(aggregate)
        return aggregate
    }

    private fun applyEvents(events: List<AggregateEvent<*>>, aggregate: Aggregate<A>) {
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

internal data class TypedAggregateEvent<T>(override val aggregateInfo: AggregateInfo,
                                           override val event: T) : AggregateEvent<T> where T : Event
