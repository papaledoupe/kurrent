package org.lodenstone.kurrent.core.eventstore

import org.lodenstone.kurrent.core.aggregate.Event
import kotlin.reflect.KClass

interface EventRegistry {
    fun <T> classForEventName(eventName: String): KClass<T>? where T : Event
    fun <T> eventNameForClass(clazz: KClass<T>): String? where T : Event
}

class MapEventRegistry(private val map: Map<String, KClass<*>>) : EventRegistry {
    private val inverseMap: Map<KClass<*>, String> = map.entries.associateBy({ it.value }) { it.key }

    override fun <T : Event> classForEventName(eventName: String) = map[eventName]?.let { it as? KClass<T> }
    override fun <T : Event> eventNameForClass(clazz: KClass<T>) = inverseMap[clazz]
}
