package com.github.cb372.pakkas.basic.actor

import akka.actor.Actor
import com.github.cb372.pakkas.basic.message.Learn
import akka.event.Logging

/**
 * Author: chris
 * Created: 4/20/13
 */
class Learner extends Actor {
  val log = Logging(context.system, this)

  def receive = {
    case Learn(value: Int) => {
      log.info(s"Learned a value! ${value}")
    }
  }
}
