package com.testpackage.child

import org.lodenstone.kurrent.core.aggregate.Event
import org.lodenstone.kurrent.spring.eventstore.EventType

@EventType(name = "another-test-event") data class AnotherTestEvent(val data: String) : Event