package org.lodenstone.kurrent.example.service

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import org.koin.dsl.module.applicationContext
import org.koin.spark.runControllers
import org.koin.spark.start
import org.koin.spark.stop
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.lodenstone.kurrent.mysqlcdc.LogReader
import org.lodenstone.kurrent.mysqlcdc.Offset
import com.github.shyiko.mysql.binlog.event.Event as LogEvent

const val offsetFilename = "offset"

class Options(parser: ArgParser) {

    val startingOffset by parser.storing("""
        Starting binlog offset in the form file/idx. If a file called '$offsetFilename' is present then the contents of
        the file will be used and this value will be ignored. This is therefore useful for setting the starting point
        for a new deployment
        """.trimIndent()) { Offset.fromString(this) }
        .default<Offset?>(null)

    val eventStoreUrl by parser.storing("""
        JDBC URL of the event store in the form jdbc:mysql://user:pass@host:port
        """.trimIndent())
        .default("jdbc:mysql://root:root@localhost:3306/cdc")

    val offsetFileName by parser.storing("""
        Name of the file in which the binlog offset is stored
        """.trimIndent())
        .default("offset")

    val persistOffsetInterval by parser.storing("""
        Interval at which binlog offset is written to the offset file
        """.trimIndent()) { toLong() }
        .default<Long>(100)
}

fun main(args: Array<String>) {
    Application(options = ArgParser(args).parseInto(::Options))
}

class Application(options: Options) : KoinComponent {

    private val reader: LogReader by inject()

    init {

        val optionsModule = applicationContext { bean { options } }

        try {
            // Start koin & spark - nonblocking
            start(modules = listOf(webModule, kurrentModule, optionsModule)) {
                runControllers()
            }

            // Start reading the event log - blocking
            reader.start()
        } finally {
            // Stop spark
            stop()
        }
    }
}
