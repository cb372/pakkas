package com.github.cb372.pakkas.basic

import akka.actor.{Props, ActorSystem}
import com.github.cb372.pakkas.basic.actor.{Acceptor, Proposer}
import akka.routing.BroadcastRouter
import com.github.cb372.pakkas.basic.message.Proposers
import java.io.File

/**
 * Author: chris
 * Created: 4/18/13
 */
object Pakkas extends App {

  // Create data dir if it doesn't exist
  val dataDir = new File("data")
  dataDir.mkdirs()

  val system = ActorSystem("pakkas")

  val learners = Seq()

  val acceptors = Seq(
    system.actorOf(Props(new Acceptor(dataDir, learners)), name = "acceptor1"),
    system.actorOf(Props(new Acceptor(dataDir, learners)), name = "acceptor2"),
    system.actorOf(Props(new Acceptor(dataDir, learners)), name = "acceptor3"),
    system.actorOf(Props(new Acceptor(dataDir, learners)), name = "acceptor4"),
    system.actorOf(Props(new Acceptor(dataDir, learners)), name = "acceptor5")
  )

  val proposers = Seq(
    system.actorOf(Props(new Proposer(dataDir, acceptors, 1, 5)), name = "proposer1"),
    system.actorOf(Props(new Proposer(dataDir, acceptors, 2, 5)), name = "proposer2"),
    system.actorOf(Props(new Proposer(dataDir, acceptors, 3, 5)), name = "proposer3"),
    system.actorOf(Props(new Proposer(dataDir, acceptors, 4, 5)), name = "proposer4"),
    system.actorOf(Props(new Proposer(dataDir, acceptors, 5, 5)), name = "proposer5")
  )

  val proposersBroadcast = system.actorOf(Props[Proposer].withRouter(
    new BroadcastRouter(routees = proposers.map(_.path.toString)))
  )

  proposersBroadcast ! Proposers(proposers)

}
