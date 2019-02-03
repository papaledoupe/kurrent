package org.lodenstone.kurrent.core.eventstore

import org.lodenstone.kurrent.core.aggregate.Command
import org.lodenstone.kurrent.core.aggregate.Event
import kotlin.reflect.KClass

interface CommandRegistry {
    fun <T> classForCommandName(commandName: String): KClass<T>? where T : Command
    fun <T> commandNameForClass(klass: KClass<T>): String? where T : Command
}

class MapCommandRegistry(map: Map<String, KClass<*>>) : CommandRegistry {
    // Using java class under the hood as two unequal KClass instances can have identical Class instances
    private val map: Map<String, Class<*>> = map.mapValues { (_, klass) -> klass.java }
    private val inverseMap: Map<Class<*>, String> = map.entries.associateBy({ it.value.java }) { it.key }

    override fun <T : Command> classForCommandName(commandName: String) = map[commandName]?.let { (it as? Class<T>)?.kotlin }
    override fun <T : Command> commandNameForClass(klass: KClass<T>) = inverseMap[klass.java]
}
