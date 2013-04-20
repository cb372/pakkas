package com.github.cb372.pakkas.basic.actor

import akka.actor.{ActorRef, Actor}
import com.github.cb372.pakkas.basic.message._
import scala.concurrent.duration._
import akka.event.Logging
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import com.github.cb372.pakkas.basic.message.Proposers
import com.github.cb372.pakkas.basic.message.PrepareResponse
import scala.Some
import com.github.cb372.pakkas.basic.message.StandForElection
import java.io.{BufferedReader, FileReader, File, FileWriter}

/**
 * Author: chris
 * Created: 4/14/13
 */
class Proposer(dataDir: File, acceptors: Seq[ActorRef], firstN: Int, nIncrement: Int) extends Actor {
  val log = Logging(context.system, this)
  val proposalNumberFile = new File(dataDir, s"${self.path.name}-proposalNumber")
  val T = 1000.millis

  def receive = leaderElection orElse preparation

  case class Leader(name: String, receivedTs: Long)

  private var leader: Option[Leader] = None

  /** The current proposal number */
  private var n = firstN

  /** The value that we are going to propose */
  private val v = Random.nextInt(10)

  private var prepareResponses: Seq[(Int, Option[Proposal])] = Nil

  /** Set to true once our proposal has been accepted */
  private var complete = false

  def leaderElection: Receive = {
    case Proposers(proposers: Seq[ActorRef]) => {
      proposers.foreach { p =>
        // send my name to all proposers every T-100 millis
        context.system.scheduler.schedule(0.millis, (T - 100.millis), p, StandForElection(self.path.name))
      }
      // Start a proposal cycle every T millis
      context.system.scheduler.schedule(T, T, self, StartPreparation)
    }
    case StandForElection(name: String) => {
      val now = System.currentTimeMillis
      leader match {
        case None => updateLeader(name, now)
        case Some(Leader(_, ts)) if (now - ts).millis > T => updateLeader(name, now)
        /*
         * The Paxons chose as president the priest whose name was last in alphabetical order
         * among the names of all priests in the Chamber, though we don’t know exactly how this was done.
         */
        case Some(Leader(pName, _)) if pName < name => updateLeader(name, now)
        case _ => // keep current leader
      }
    }
  }

  def preparation: Receive = {
    case StartPreparation => {
      if (!complete && iAmTheLeader) {
        incrementProposalNumber()
        acceptors.foreach(_ ! PrepareRequest(n))
      }
    }
    case resp @ PrepareResponse(proposalNumber, prevProposal) => {
      log.debug(s"Received a prepare response: ${resp}. Current n is ${n}")

      // make a note of the response
      prepareResponses = ((proposalNumber, prevProposal)) +: prepareResponses
      log.debug(s"Added response to prepareResponses list. ${prepareResponses}")

      // remove any old responses that we don't need any more
      prepareResponses = prepareResponses.filter { case (pN, _) => pN == n }
      log.debug(s"Removed responses with old proposal numbers. ${prepareResponses}")

      // if we have a quorum of responses, send an AcceptRequest
      if (prepareResponses.size > acceptors.size / 2) {
        log.info(s"Received PrepareResponses from a majority of acceptors. ${prepareResponses}")

        // Choose the value of the highest numbered proposal amongst the responses,
        // or our own value if there were no responses
        val valueToPropose = prepareResponses.collect { case (_, Some(proposal)) => proposal }
                        .sortBy { case Proposal(pN, _) => pN }
                        .reverse
                        .headOption
                        .map { case Proposal(_, value) => value }
                        .getOrElse(v)
        for (acceptor <- acceptors) acceptor ! AcceptRequest(Proposal(n, valueToPropose))
        log.debug(s"Sent AcceptRequest for proposal(n=${n}, v=${valueToPropose})")

        prepareResponses = Nil
        log.debug(s"Cleared prepareResponses list")
      }
    }
    case AcceptResponse(proposal) => {
      log.info(s"Yay! My proposal ${proposal} was accepted by ${sender.path.name}")
      complete = true
    }
  }

  private def incrementProposalNumber() {
    // Increment the proposal number
    n += nIncrement
    // Save the new value to a file
    writeProposalNumberToFile(n)
  }

  def writeProposalNumberToFile(n: Int) {
    val writer = new FileWriter(proposalNumberFile)
    try {
      writer.write(n.toString)
      log.debug(s"Wrote proposal number ${n} to file")
    } finally {
      writer.close()
    }
  }

  def readProposalNumberFromFile(): Int = {
    val reader: BufferedReader = new BufferedReader(new FileReader(proposalNumberFile))
    try {
      val n = reader.readLine().toInt
      log.debug(s"Read proposal number ${n} from file")
      n
    } finally {
      reader.close()
    }
  }

  private def updateLeader(name: String, now: Long) {
    leader = Some(Leader(name, now))
    log.debug(s"Updated leader, it is now $name")
  }

  private def iAmTheLeader = {
    leader map { l =>
      l.name == self.path.name
    } getOrElse {
      // Haven't received any messages from proposers. That's weird.
      // Just assume I'm the leader of this solipsistic world.
      true
    }
  }

  override def preStart() {
    if (proposalNumberFile.exists()) {
      n = readProposalNumberFromFile()
    }
  }
}
