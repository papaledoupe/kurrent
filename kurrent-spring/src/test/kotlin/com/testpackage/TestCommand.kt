package com.testpackage

import org.lodenstone.kurrent.core.aggregate.Command
import org.lodenstone.kurrent.core.aggregate.Event
import org.lodenstone.kurrent.spring.eventstore.CommandType
import org.lodenstone.kurrent.spring.eventstore.EventType

@CommandType("testCommand") object TestCommand : Command