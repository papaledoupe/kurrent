package org.lodenstone.kurrent.example.service

import io.restassured.RestAssured.`when`
import io.restassured.RestAssured.given
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.ClassRule
import org.junit.Test
import org.slf4j.LoggerFactory
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.time.Duration
import kotlin.math.exp

class ComponentTest {

    companion object {
        @ClassRule @JvmField val store = KComposeContainer(File("docker-compose.test.yml"))
                .withExposedService("example-service", 4567,
                        Wait.forHttp("/health").forStatusCode(200))
                .withTailChildContainers(true)
    }

    private val baseUrl get() = "http://" +
            "${store.getServiceHost("example-service", 4567)}:" +
            "${store.getServicePort("example-service", 4567)}"

    @Test fun `it works`() {

        // Start the game
        given().body("""{ "command": "StartGame", "data": {} }""")
                .`when`().post("$baseUrl/command/TicTacToe/1")
                .then().log().ifValidationFails().statusCode(202)

        // Take a turn
        given().body("""
            {
                "command": "TakeTurn",
                 "data": {
                    "player": "X",
                    "i": 2,
                    "j": 0
                 }
            }""".trimIndent())
                .`when`().post("$baseUrl/command/TicTacToe/1/version/1")
                .then().log().ifValidationFails().statusCode(202)

        assertGameState("""

               |   |
            ---+---+---
               |   |
            ---+---+---
             X |   |

            Winner: -

        """.trimIndent())

        // Take a turn
        given().body("""
            {
                "command": "TakeTurn",
                 "data": {
                    "player": "O",
                    "i": 1,
                    "j": 0
                 }
            }""".trimIndent())
                .`when`().post("$baseUrl/command/TicTacToe/1/version/2")
                .then().log().ifValidationFails().statusCode(202)

        assertGameState("""

               |   |
            ---+---+---
             O |   |
            ---+---+---
             X |   |

            Winner: -

        """.trimIndent())

        // Take a turn
        given().body("""
            {
                "command": "TakeTurn",
                 "data": {
                    "player": "X",
                    "i": 1,
                    "j": 1
                 }
            }""".trimIndent())
                .`when`().post("$baseUrl/command/TicTacToe/1/version/3")
                .then().log().ifValidationFails().statusCode(202)

        assertGameState("""

               |   |
            ---+---+---
             O | X |
            ---+---+---
             X |   |

            Winner: -

        """.trimIndent())

        // Take a turn
        given().body("""
            {
                "command": "TakeTurn",
                 "data": {
                    "player": "X",
                    "i": 0,
                    "j": 2
                 }
            }""".trimIndent())
                .`when`().post("$baseUrl/command/TicTacToe/1/version/4")
                .then().log().ifValidationFails().statusCode(202)

        assertGameState("""

               |   | X
            ---+---+---
             O | X |
            ---+---+---
             X |   |

            Winner: X

        """.trimIndent())
    }

    private fun assertGameState(expected: String) {
        // Struggling to match multi-line strings... so stripping whitespace before comparing
        val actual = `when`().get("$baseUrl/query/TicTacToe/1")
                .then().log().ifValidationFails().statusCode(200).extract().body().asString()
        assertThat(actual.replace("\\s+", ""), equalTo(expected.replace("\\s+", "")))
    }
}

// testcontainers is annoying to use with Kotlin...
class KComposeContainer(composeFile: File) : DockerComposeContainer<KComposeContainer>(composeFile)
