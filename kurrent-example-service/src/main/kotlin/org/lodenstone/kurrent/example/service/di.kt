package org.lodenstone.kurrent.example.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.experimental.runBlocking
import org.awaitility.Awaitility.await
import org.jdbi.v3.core.ConnectionException
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import org.jdbi.v3.sqlobject.kotlin.attach
import org.koin.dsl.module.applicationContext
import org.koin.spark.controller
import org.lodenstone.kurrent.core.aggregate.AggregateServiceRegistry
import org.lodenstone.kurrent.core.aggregate.AggregateSnapshotStore
import org.lodenstone.kurrent.core.aggregate.InMemoryAggregateSnapshotStore
import org.lodenstone.kurrent.core.aggregate.MapAggregateServiceRegistry
import org.lodenstone.kurrent.core.eventstore.*
import org.lodenstone.kurrent.eventstore.sql.EventRepository
import org.lodenstone.kurrent.eventstore.sql.JdbiEventStoreClient
import org.lodenstone.kurrent.mysqlcdc.DefaultEventRouter
import org.lodenstone.kurrent.mysqlcdc.EventRouter
import org.lodenstone.kurrent.mysqlcdc.LogReader
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit

val webModule = applicationContext {
    controller { CommandController(get(), get(), get()) }
    controller { QueryController(get()) }
    controller { HealthController() }
}

val kurrentModule = applicationContext {

    val logger = LoggerFactory.getLogger("di-kurrent-module")

    bean {
        (MapAggregateServiceRegistry() as AggregateServiceRegistry).also {
            it.register(get<TicTacToeService>())
        }
    }

    // Serialization

    bean { jacksonObjectMapper() }

    bean {
        MapCommandRegistry(mapOf(
                "StartGame" to TicTacToe.Commands.StartGame::class,
                "TakeTurn" to TicTacToe.Commands.TakeTurn::class
        )) as CommandRegistry
    }

    bean {
        MapEventRegistry(mapOf(
                "GameStarted" to TicTacToe.Events.GameStarted::class,
                "TurnTaken" to TicTacToe.Events.TurnTaken::class
        )) as EventRegistry
    }

    // Event reading

    bean { DefaultEventRouter(get(), get(), get()) as EventRouter }

    bean {
        val options: Options = get()
        val eventStoreURI = parseJdbcUrl(options.eventStoreUrl)
        val reader = LogReader(
                options.startingOffset,
                options.offsetFileName,
                eventStoreURI.host,
                eventStoreURI.port,
                eventStoreURI.username,
                eventStoreURI.password,
                options.persistOffsetInterval,
                get()
        )
        Runtime.getRuntime().addShutdownHook(Thread { runBlocking { reader.stop() } })
        reader
    }

    // Event writing

    bean {
        val options: Options = get()
        val jdbi = Jdbi.create(options.eventStoreUrl)
        jdbi.installPlugin(SqlObjectPlugin()).installPlugin(KotlinPlugin()).installPlugin(KotlinSqlObjectPlugin())
        var dao: EventRepository? = null
        await()
                .atMost(1, TimeUnit.MINUTES)
                .pollInterval(5, TimeUnit.SECONDS)
                .until {
                    return@until try {
                        dao = jdbi.open().attach()
                        true
                    } catch (e: ConnectionException) {
                        logger.info("Failed to connect to event store, retrying in 5 s (max 60 s)")
                        false
                    }
                }
        JdbiEventStoreClient(dao!!, get(), get()) as EventStoreClient
    }

    // Aggregates

    bean {
        TicTacToeService(get(), get())
    }

    bean { InMemoryAggregateSnapshotStore<TicTacToe>() as AggregateSnapshotStore<TicTacToe> }
}

private val URI.username get() = userInfo.split(":").firstOrNull() ?: ""
private val URI.password get() = userInfo.split(":").lastOrNull() ?: ""

fun parseJdbcUrl(jdbcUrl: String): URI {
    if (!jdbcUrl.startsWith("jdbc:")) {
        throw IllegalArgumentException("JDBC URL must start with jdbc:")
    }
    return URI(jdbcUrl.substring(startIndex = 5))
}
