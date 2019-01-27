package org.lodenstone.kurrent.example.service

import org.lodenstone.kurrent.core.aggregate.*
import org.lodenstone.kurrent.core.eventstore.EventStore

class TicTacToe {

    enum class Player { O, X }

    private val spaces: Array<Array<Player?>> = arrayOf(
            arrayOfNulls(3),
            arrayOfNulls(3),
            arrayOfNulls(3)
    )

    operator fun get(i: Int, j: Int): Player? {
        return spaces[i][j]
    }

    operator fun set(i: Int, j: Int, player: Player) {
        spaces[i][j] = player
    }

    private fun assertSpaceExists(i: Int, j: Int) {
        if (i !in 0..2 || j !in 0..2) {
            throw RejectedCommandException("Space ($i,$j) does not exist")
        }
    }

    private fun assertSpaceFree(i: Int, j: Int) {
        if (get(i, j) != null) {
            throw RejectedCommandException("Space ($i,$j) is occupied")
        }
    }

    fun row(i: Int) = spaces[i]
    fun column(j: Int) = arrayOf(spaces[0][j], spaces[1][j], spaces[2][j])

    val rows get() = (0..2).map(::row)
    val columns get() = (0..2).map(::column)
    val diagonal get() = arrayOf(spaces[0][0], spaces[1][1], spaces[2][2])
    val antidiagonal get() = arrayOf(spaces[0][2], spaces[1][1], spaces[2][0])

    val vectors: Iterable<Array<Player?>> get() = rows + columns + listOf(diagonal, antidiagonal)

    val Array<Player?>.winner: Player? get() {
        if (distinct().size == 1) {
            return get(0)
        }
        return null
    }

    val winner: Player? get() {
        vectors.forEach { it.winner?.let { return it } }
        return null
    }

    interface Commands {
        object StartGame : Command, Initializing
        data class TakeTurn(val player: Player, val i: Int, val j: Int) : Command
    }

    interface Events {
        object GameStarted : Event
        data class TurnTaken(val player: Player, val i: Int, val j: Int) : Event
    }

    companion object {

        const val aggregateType = "TicTacToe"

        val builder = aggregate<TicTacToe> {

            command<Commands.StartGame> { game ->
                listOf(Events.GameStarted)
            }

            command<Commands.TakeTurn> { game, cmd ->
                game.assertSpaceExists(cmd.i, cmd.j)
                game.assertSpaceFree(cmd.i, cmd.j)
                listOf(Events.TurnTaken(cmd.player, cmd.i, cmd.j))
            }

            event<Events.TurnTaken> { game, evt ->
                game[evt.i, evt.j] = evt.player
                game
            }
        }
    }
}

class TicTacToeService(eventStore: EventStore, snapshotStore: AggregateSnapshotStore<TicTacToe>)
    : AggregateService<TicTacToe>(eventStore, snapshotStore) {
    override val aggregateType = TicTacToe.aggregateType
    override val aggregateBuilder = TicTacToe.builder
    override fun initializeState() = TicTacToe()
}
