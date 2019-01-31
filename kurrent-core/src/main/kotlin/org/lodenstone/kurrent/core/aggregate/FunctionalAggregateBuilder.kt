package org.lodenstone.kurrent.core.aggregate

import org.lodenstone.kurrent.core.util.loggerFor
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

fun <D> aggregate(init: FunctionalAggregateBuilder<D>.() -> Unit): FunctionalAggregateBuilder<D> {
    val fab = FunctionalAggregateBuilder<D>()
    init(fab)
    return fab
}

class FunctionalAggregateBuilder<D> : Aggregate.Builder<D> {

    val commands = mutableMapOf<KClass<*>, (D?, Command) -> List<Event>>()
    val events = mutableMapOf<KClass<*>, (D?, Event) -> D>()

    inline fun <reified T> initializingCommand(noinline handler: (T) -> Initialized) where T : Initialize {
        commands[T::class] = { data, cmd ->
            if (data != null) throw RejectedCommandException("already initialized")
            listOf(handler(cmd as T))
        }
    }

    inline fun <reified T> initializingEvent(noinline handler: (T) -> D) where T : Initialized {
        events[T::class] = { data, evt ->
            if (data != null) throw IllegalStateException("already initialized")
            handler(evt as T)
        }
    }

    inline fun <reified T> command(noinline handler: D.(T) -> List<Event>) where T : Command {
        commands[T::class] = { data, cmd ->
            handler(data ?: throw IllegalStateException("not initialized, must send an Initialize command first"), cmd as T)
        }
    }

    inline fun <reified T> event(noinline handler: D.(T) -> D) where T : Event {
        events[T::class] = { data, evt ->
            handler(data ?: throw IllegalStateException("not initialized, must send an Initialize command first"), evt as T)
        }
    }

    override fun build(data: D?, aggregateInfo: AggregateInfo): Aggregate<D> {
        if (commands.keys.none { cmdClass -> cmdClass.isSubclassOf(Initialize::class) }) {
            throw IllegalStateException("at least one ${Initialize::class.simpleName} command must be handled")
        }
        if (events.keys.none { cmdClass -> cmdClass.isSubclassOf(Initialized::class) }) {
            throw IllegalStateException("at least one ${Initialized::class.simpleName} event must be handled")
        }
        return FunctionalAggregate(data, aggregateInfo, commands, events)
    }
}

internal class FunctionalAggregate<D>(data: D?,
                                      info: AggregateInfo,
                                      private val commands: Map<KClass<*>, (D?, Command) -> List<Event>>,
                                      private val events: Map<KClass<*>, (D?, Event) -> D>) : Aggregate<D> {

    companion object {
        val log = loggerFor<FunctionalAggregate<*>>()
    }

    override var data: D? = data ; private set
    override var info: AggregateInfo = info ; private set

    override fun handle(command: Command): List<Event> {
        val handler = commands[command::class] ?: return emptyList()
        val events = handler(data, command)
        log.debug("$info cmd ${command::class.simpleName} -> ${events.map { it::class.simpleName }}")
        return events
    }

    override fun apply(event: Event) {
        val handler = events[event::class]
        if (handler != null) {
            data = handler(data, event)
        }
        info = info.copy(version = info.version + 1)
        log.debug("$info evt ${event::class.simpleName}")
    }
}
