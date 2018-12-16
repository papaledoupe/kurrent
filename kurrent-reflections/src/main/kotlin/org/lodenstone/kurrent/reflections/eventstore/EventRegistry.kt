package org.lodenstone.kurrent.reflections.eventstore

import org.lodenstone.kurrent.core.aggregate.Event
import org.lodenstone.kurrent.core.eventstore.EventRegistry
import org.lodenstone.kurrent.core.util.loggerFor
import org.reflections.Reflections
import kotlin.reflect.KClass

class ScanningEventRegistry(private val reflections: Reflections) : EventRegistry {

    companion object {
        val logger = loggerFor<ScanningEventRegistry>()
    }

    private var eventNameToClass = mapOf<String, Class<*>>()
    private var classToEventName = mapOf<Class<*>, String>()

    fun scan() {
        val nameToClass = mutableMapOf<String, Class<*>>()
        reflections.getSubTypesOf(Event::class.java).forEach { clazz ->
            val name = clazz.getAnnotation(SerializedAs::class.java)?.name
                    ?: throw IllegalStateException("Event class ${clazz.name} requires @${SerializedAs::class.simpleName} annotation")
            if (nameToClass.containsKey(name)) {
                throw IllegalStateException("Event class name duplicated: $name")
            }
            nameToClass[name] = clazz
            logger.info("Registered event: $name")
        }
        eventNameToClass = nameToClass
        classToEventName = eventNameToClass.entries.associateBy({ it.value }) { it.key }.toMap()
    }

    override fun <T> classForEventName(eventName: String): KClass<T>? where T : Event
            = eventNameToClass[eventName]?.kotlin?.let { @Suppress("UNCHECKED_CAST") it as? KClass<T> }

    override fun <T> eventNameForClass(clazz: KClass<T>) where T : Event = classToEventName[clazz.java]
}