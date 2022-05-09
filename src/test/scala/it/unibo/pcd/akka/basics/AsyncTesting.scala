package it.unibo.pcd.akka.basics

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import it.unibo.pcd.akka.basics.e02oopstyle.*
import it.unibo.pcd.akka.basics.e03state.GuessGame.*
import PingPong.*

class AsyncTesting extends AnyWordSpec with Matchers with BeforeAndAfterAll:
  val testKit = ActorTestKit() // creates an actor system (one per a set of tests)

  // Shutdown the actor system after all the tests
  override def afterAll(): Unit = testKit.shutdownTestKit()

  "A testkit" must {
    "support verifying a response" in {
      val pinger = testKit.spawn[PingPong](Behaviors.setup(new PingPonger(_)), "ping")
      val probe = testKit.createTestProbe[PingPong]()//creo un attore ispezionabile

      testKit.system.log.info("Sending ping")
      pinger ! Ping(probe.ref)
      probe.expectMessage(Pong(pinger.ref))

      testKit.system.log.info("Sending pong")
      pinger ! Pong(probe.ref)
      probe.expectMessage(Ping(pinger.ref))
    }

    "support mocking" in {
      val BAD_GUESS = -1
      val SECRET = 50
      val NUM_ATTEMPTS = 3

      val gameRef = testKit.spawn(game(SECRET, NUM_ATTEMPTS))
      val playerProbe = testKit.createTestProbe[PlayerMessage]()
      val playerMockBehavior = Behaviors.receiveMessage[PlayerMessage] {
        case NewInput =>
          gameRef ! Guess(BAD_GUESS, playerProbe.ref)
          Behaviors.same
        case _ => Behaviors.ignore
      }
      val playerMock = testKit.spawn(Behaviors.monitor(playerProbe.ref, playerMockBehavior))
      for (i <- 0 until NUM_ATTEMPTS - 1) do
        playerMock ! NewInput
        playerProbe.expectMessage(NewInput)
        playerProbe.expectMessage(NotGuessed(TooSmall(BAD_GUESS), NUM_ATTEMPTS - 1 - i))

      playerMock ! NewInput
      playerProbe.expectMessage(NewInput)
      playerProbe.expectMessageType[Loss.type]
    }
  }
