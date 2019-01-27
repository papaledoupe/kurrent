package org.lodenstone.kurrent.core.aggregate

data class Versioned<A>(val aggregate: A, val version: Long)

interface AggregateSnapshotStore<A> {
    fun getLatest(aggregateId: String): Versioned<A>?
    fun get(aggregateId: String, aggregateVersion: Long): A?
    fun put(aggregate: Aggregate<A>)
}

class InMemoryAggregateSnapshotStore<A> : AggregateSnapshotStore<A> {

    private var map = mutableMapOf<String, MutableMap<Long, A>>()

    override fun getLatest(aggregateId: String) = map[aggregateId]
            ?.maxBy { (version, _) -> version }
            ?.let { (version, agg) -> Versioned(agg, version) }

    override fun get(aggregateId: String, aggregateVersion: Long) = map[aggregateId]?.get(aggregateVersion)

    override fun put(aggregate: Aggregate<A>) {
        map.getOrPut(aggregate.info.id, ::mutableMapOf)[aggregate.info.version] = aggregate.data
    }
}