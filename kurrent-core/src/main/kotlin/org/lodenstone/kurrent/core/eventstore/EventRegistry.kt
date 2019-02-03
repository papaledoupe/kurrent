package org.lodenstone.kurrent.core.eventstore

import org.lodenstone.kurrent.core.aggregate.Event
import org.lodenstone.kurrent.core.aggregate.FunctionalAggregateBuilder
import kotlin.reflect.KClass

interface EventRegistry {
    fun <T> classForEventName(eventName: String): KClass<T>? where T : Event
    fun <T> eventNameForClass(klass: KClass<T>): String? where T : Event
}

class MapEventRegistry(map: Map<String, KClass<*>>) : EventRegistry {
    // Using java class under the hood as two unequal KClass instances can have identical Class instances
    private val map: Map<String, Class<*>> = map.mapValues { (_, klass) -> klass.java }
    private val inverseMap: Map<Class<*>, String> = map.entries.associateBy({ it.value.java }) { it.key }

    override fun <T : Event> classForEventName(eventName: String) = map[eventName]?.let { (it as? Class<T>)?.kotlin }
    override fun <T : Event> eventNameForClass(klass: KClass<T>) = inverseMap[klass.java]
}
