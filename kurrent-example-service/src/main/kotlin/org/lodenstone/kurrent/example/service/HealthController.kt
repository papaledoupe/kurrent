package org.lodenstone.kurrent.example.service

import spark.kotlin.get

class HealthController {
    init {
        get("/health") {
            response.status(200)
        }
    }
}
