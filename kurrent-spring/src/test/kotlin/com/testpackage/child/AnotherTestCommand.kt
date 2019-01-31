package com.testpackage.child

import org.lodenstone.kurrent.core.aggregate.Command
import org.lodenstone.kurrent.core.aggregate.Event
import org.lodenstone.kurrent.spring.eventstore.CommandType
import org.lodenstone.kurrent.spring.eventstore.EventType

@CommandType(name = "another-test-command") data class AnotherTestCommand(val data: String) : Command