package com.testpackage

import org.lodenstone.kurrent.core.aggregate.Event
import org.lodenstone.kurrent.spring.eventstore.EventType

@EventType("testEvent") object TestEvent : Event