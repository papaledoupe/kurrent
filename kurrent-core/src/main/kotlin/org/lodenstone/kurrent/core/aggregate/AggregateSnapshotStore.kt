package org.lodenstone.kurrent.core.aggregate

data class Versioned<A>(val aggregate: A, val version: Long)

interface AggregateSnapshotStore<D> {
    fun getLatest(aggregateId: String): Versioned<D>?
    fun get(aggregateId: String, aggregateVersion: Long): D?
    fun put(aggregateInfo: AggregateInfo, data: D)
}

class InMemoryAggregateSnapshotStore<A> : AggregateSnapshotStore<A> {

    private var map = mutableMapOf<String, MutableMap<Long, A>>()

    override fun getLatest(aggregateId: String) = map[aggregateId]
            ?.maxBy { (version, _) -> version }
            ?.let { (version, agg) -> Versioned(agg, version) }

    override fun get(aggregateId: String, aggregateVersion: Long) = map[aggregateId]?.get(aggregateVersion)

    override fun put(aggregateInfo: AggregateInfo, data: A) {
        map.getOrPut(aggregateInfo.id, ::mutableMapOf)[aggregateInfo.version] = data
    }
}