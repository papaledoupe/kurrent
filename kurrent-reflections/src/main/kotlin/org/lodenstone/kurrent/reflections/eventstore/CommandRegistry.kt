package org.lodenstone.kurrent.reflections.eventstore

import org.lodenstone.kurrent.core.aggregate.Command
import org.lodenstone.kurrent.core.eventstore.CommandRegistry
import org.lodenstone.kurrent.core.util.loggerFor
import org.reflections.Reflections
import kotlin.reflect.KClass

class ScanningCommandRegistry(private val reflections: Reflections) : CommandRegistry {

    companion object {
        val logger = loggerFor<ScanningCommandRegistry>()
    }

    private var commandNameToClass = mapOf<String, Class<*>>()
    private var classToCommandName = mapOf<Class<*>, String>()

    fun scan() {
        val nameToClass = mutableMapOf<String, Class<*>>()
        reflections.getSubTypesOf(Command::class.java).forEach { clazz ->
            val name = clazz.getAnnotation(SerializedAs::class.java)?.name
                    ?: throw IllegalStateException("Command class ${clazz.name} requires @${SerializedAs::class.simpleName} annotation")
            if (nameToClass.containsKey(name)) {
                throw IllegalStateException("Command class name duplicated: $name")
            }
            nameToClass[name] = clazz
            logger.info("Registered command: $name")
        }
        commandNameToClass = nameToClass
        classToCommandName = commandNameToClass.entries.associateBy({ it.value }) { it.key }.toMap()
    }

    override fun <T> classForCommandName(commandName: String): KClass<T>? where T : Command
            = commandNameToClass[commandName]?.kotlin?.let { @Suppress("UNCHECKED_CAST") it as? KClass<T> }

    override fun <T> commandNameForClass(clazz: KClass<T>) where T : Command = classToCommandName[clazz.java]
}