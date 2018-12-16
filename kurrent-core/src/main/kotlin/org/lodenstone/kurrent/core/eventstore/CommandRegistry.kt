package org.lodenstone.kurrent.core.eventstore

import org.lodenstone.kurrent.core.aggregate.Command
import org.lodenstone.kurrent.core.aggregate.Event
import kotlin.reflect.KClass

interface CommandRegistry {
    fun <T> classForCommandName(commandName: String): KClass<T>? where T : Command
    fun <T> commandNameForClass(clazz: KClass<T>): String? where T : Command
}

class MapCommandRegistry(private val map: Map<String, KClass<*>>) : CommandRegistry {
    private val inverseMap: Map<KClass<*>, String> = map.entries.associateBy({ it.value }) { it.key }

    override fun <T : Command> classForCommandName(commandName: String) = map[commandName]?.let { it as? KClass<T> }
    override fun <T : Command> commandNameForClass(clazz: KClass<T>) = inverseMap[clazz]
}
