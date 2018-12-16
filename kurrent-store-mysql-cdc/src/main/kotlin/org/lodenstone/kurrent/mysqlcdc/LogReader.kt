package org.lodenstone.kurrent.mysqlcdc

import com.github.shyiko.mysql.binlog.BinaryLogClient
import com.github.shyiko.mysql.binlog.event.EventHeader
import com.github.shyiko.mysql.binlog.event.WriteRowsEventData
import com.github.shyiko.mysql.binlog.event.deserialization.ByteArrayEventDataDeserializer
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.timeunit.TimeUnit
import org.lodenstone.kurrent.core.util.loggerFor
import sun.plugin.dom.exception.InvalidStateException
import java.io.File
import com.github.shyiko.mysql.binlog.event.Event as LogEvent
import com.github.shyiko.mysql.binlog.event.EventData as LogEventData
import com.github.shyiko.mysql.binlog.event.EventHeader as LogEventHeader
import com.github.shyiko.mysql.binlog.event.EventType as LogEventType

data class Offset(val file: String, val position: Long) {

    companion object {
        fun fromString(string: String): Offset? {
            if (string.isBlank()) {
                return null
            }
            val (file, startingOffset) = string.split('/')
            return startingOffset.toLongOrNull()?.let { Offset(file, it) }
        }
    }

    override fun toString() = "$file/$position"
}

class LogReader(private val startingOffset: Offset?,
                private val offsetFile: String,
                host: String,
                port: Int,
                username: String,
                password: String,
                private val persistOffsetInterval: Long,
                private val router: EventRouter) {

    companion object {
        val logger = loggerFor<LogReader>()
    }

    private val logClient = BinaryLogClient(host, port, username, password)

    private var persistOffsetJob: Job? = null

    fun start() {

        configureDeserializers()

        val file = File(offsetFile)

        val (startFile, startPos) = determineStartPosition(file)
        logClient.binlogFilename = startFile
        logClient.binlogPosition = startPos

        logClient.registerEventListener(::handle)
        persistOffsetJob = launch { persistOffset(file) }
        logClient.connect()
    }

    suspend fun stop() {
        logger.info("Disconnecting from DB...")
        logClient.disconnect()
        logger.info("Waiting for offset to save...")
        persistOffsetJob?.cancelAndJoin()
        logger.info("Stopped")
    }

    private fun handle(event: LogEvent) {
        when (event.getData<LogEventData>()) {
            is WriteRowsEventData -> {
                logger.debug("Write data: ${event.getData<WriteRowsEventData>()}")
                event.getData<WriteRowsEventData>().rows.map { data ->

                }
                router.route(event)
            }
            else -> logger.trace("Unsupported event type received: ${event.getHeader<EventHeader>().eventType}")
        }
    }

    private fun determineStartPosition(file: File): Offset {
        return if (!file.exists()) {
            startingOffset ?: throw IllegalStateException("No current offset file, and no starting offset specified")
        } else {
            readOffset(file)
        }
    }

    private fun readOffset(file: File): Offset {
        return Offset.fromString(file.readText(Charsets.UTF_8))
                ?: throw InvalidStateException("Could not parse offset file")
    }

    private suspend fun CoroutineScope.persistOffset(file: File) {
        var offset = logClient.offsetString
        do {
            delay(persistOffsetInterval, TimeUnit.SECONDS)
            if (logClient.offsetString == offset) {
                logger.debug("binlog offset unchanged at $offset")
            } else {
                offset = logClient.offsetString
                logger.debug("binlog offset changed, writing to file: $offset")
                file.writeText(offset, Charsets.UTF_8)
            }
        } while (isActive)
    }

    private fun configureDeserializers() {
        val eventDeserializer = EventDeserializer()
        eventDeserializer.setCompatibilityMode(EventDeserializer.CompatibilityMode.DATE_AND_TIME_AS_LONG)
        eventDeserializer.setEventDataDeserializer(LogEventType.WRITE_ROWS, ByteArrayEventDataDeserializer())
        logClient.setEventDeserializer(eventDeserializer)
    }
}

private val BinaryLogClient.offsetString get() = "$binlogFilename/$binlogPosition"