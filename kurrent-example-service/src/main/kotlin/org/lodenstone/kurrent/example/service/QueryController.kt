package org.lodenstone.kurrent.example.service

import org.lodenstone.kurrent.core.aggregate.AggregateSnapshotStore
import org.lodenstone.kurrent.core.aggregate.NoSuchAggregateException
import spark.kotlin.get

class QueryController(private val snapshotStore: AggregateSnapshotStore<TicTacToe>) {
    init {
        get("/query/TicTacToe/:id") {
            val id = request.params(":id")
            response.type("text/plain")
            snapshotStore.getLatest(id)?.printBoard()
                    ?: throw NoSuchAggregateException(TicTacToe.aggregateType, id)
        }
    }
}

private const val horizontalRule = "---+---+---"

private fun TicTacToe.printRow(i: Int) = " ${row(i)[0] ?: ' '} | ${row(i)[1] ?: ' '} |${if (row(i)[2] != null) " ${row(i)[2]}" else ""}"

private fun TicTacToe.printBoard() = """
${printRow(0)}
$horizontalRule
${printRow(1)}
$horizontalRule
${printRow(2)}

Winner: ${winner ?: "-"}
"""

