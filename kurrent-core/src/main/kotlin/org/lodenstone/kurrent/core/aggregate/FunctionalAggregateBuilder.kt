package org.lodenstone.kurrent.core.aggregate

import org.lodenstone.kurrent.core.util.loggerFor
import kotlin.reflect.KClass

fun <D> aggregate(init: FunctionalAggregateBuilder<D>.() -> Unit): FunctionalAggregateBuilder<D> {
    val fab = FunctionalAggregateBuilder<D>()
    init(fab)
    return fab
}

class FunctionalAggregateBuilder<D> : Aggregate.Builder<D> {

    val commands = mutableMapOf<KClass<*>, (D, Command) -> List<Event>>()
    val events = mutableMapOf<KClass<*>, (D, Event) -> D>()

    inline fun <reified T> command(noinline handler: (D, T) -> List<Event>) where T : Command {
        commands[T::class] = { dat, cmd -> handler(dat, cmd as T) }
    }

    inline fun <reified T> command(noinline handler: (D) -> List<Event>) where T : Command {
        command<T> { dat, _ -> handler(dat) }
    }

    inline fun <reified T> event(noinline handler: (D, T) -> D) where T : Event {
        events[T::class] = { dat, evt -> handler(dat, evt as T) }
    }

    inline fun <reified T> event(noinline handler: (D) -> D) where T : Event {
        event<T> { dat, _ -> handler(dat) }
    }

    override fun build(data: D, aggregateInfo: AggregateInfo): Aggregate<D> {
        return FunctionalAggregate(data, aggregateInfo, commands, events)
    }
}

internal class FunctionalAggregate<D>(data: D,
                                      info: AggregateInfo,
                                      private val commands: Map<KClass<*>, (D, Command) -> List<Event>>,
                                      private val events: Map<KClass<*>, (D, Event) -> D>) : Aggregate<D> {

    companion object {
        val log = loggerFor<FunctionalAggregate<*>>()
    }

    override var data: D = data ; private set
    override var info: AggregateInfo = info ; private set

    override fun handle(commandVersion: Long, command: Command): List<Event> {
        if (info.version != commandVersion) {
            throw AggregateVersionConflictException
        }
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
        log.debug("$info evt ${event::class.simpleName}}")
    }
}
