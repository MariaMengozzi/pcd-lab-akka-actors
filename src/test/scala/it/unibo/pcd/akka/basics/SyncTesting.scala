package it.unibo.pcd.akka.basics

import akka.actor.testkit.typed.Effect
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.funsuite.AnyFunSuite

object ActorUnderTest:
  enum Response:
    case Pong
  import Response.*

  enum Command:
    case SpawnChild
    case SpawnChildAnonymous
    case SayHelloToChild
    case SayHelloToAnonymousChild
    case Ping(replyTo: ActorRef[Pong.type])
  import Command.*

  val child = Behaviors.receiveMessage[Any](_ => Behaviors.same)

  def apply(): Behavior[Command] = Behaviors.receive {
    case (ctx, SpawnChild) =>
      ctx.spawn(child, "child")
      Behaviors.same
    case (ctx, SpawnChildAnonymous) =>
      ctx.spawnAnonymous(child)
      Behaviors.same
    case (_, Ping(replyTo)) =>
      replyTo ! Pong
      Behaviors.same
    case (ctx, SayHelloToChild) =>
      ctx.child("child").get.unsafeUpcast[String] ! "hello"
      Behaviors.same
    case (ctx, SayHelloToAnonymousChild) =>
      ctx.spawnAnonymous(child) ! "hello"
      Behaviors.same
  }

class SyncTesting extends AnyFunSuite:
  import ActorUnderTest.*
  import Response.*
  import Command.*

  test("Child actor spawning") {
    val testKit = BehaviorTestKit(ActorUnderTest())
    testKit.run(SpawnChild) //esegui l'handler di quando ti arriva SpawnChild e sulla base di questo gli dico cosa mi aspetto
    testKit.expectEffect(Effect.Spawned(child, "child"))
    testKit.run(SpawnChildAnonymous)
    testKit.expectEffect(Effect.SpawnedAnonymous(child))
  }

  test("Sending messages to another actor") {
    val testKit = BehaviorTestKit(ActorUnderTest())
    val inbox = TestInbox[Pong.type]()
    testKit.run(Ping(inbox.ref))
    inbox.expectMessage(Pong)
  }

  test("Sending messages to child") {
    val testKit = BehaviorTestKit(ActorUnderTest())
    testKit.run(SpawnChild)
    val childInbox = testKit.childInbox[String]("child")
    testKit.run(SayHelloToChild)
    childInbox.expectMessage("hello")

    // For anonymous children the actor names are generated in a deterministic way:
    testKit.run(SayHelloToAnonymousChild)
    testKit.expectEffectType[Effect.Spawned[String]] // must consume old effect
    val child: Effect.SpawnedAnonymous[String] = testKit.expectEffectType[Effect.SpawnedAnonymous[String]]
    val achildInbox = testKit.childInbox(child.ref)
    achildInbox.expectMessage("hello")
  }
