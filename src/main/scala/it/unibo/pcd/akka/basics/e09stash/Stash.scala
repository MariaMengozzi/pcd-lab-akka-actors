package it.unibo.pcd.akka.basics.e09stash

import akka.actor.typed.{ActorSystem, Behavior, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import it.unibo.pcd.akka.basics.e09stash.Node.Command.{MoveTo, Stop, Start}

object Node:
  enum Command:
    case Start(x: Double, y: Double)
    case MoveTo(x: Double, y: Double)
    case Stop
  export Command.*
  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    {
      Behaviors.withStash[Command](100) { stash =>
        Behaviors.receiveMessage {
          case Start(x, y) =>
            ctx.log.info(s"Started!! $x, $y")
            stash.unstashAll(initialised(x, y))
          case other =>
            ctx.log.info(s"Not already initialised, $other in stash")
            stash.stash(other) // se lo togliessi i messaggi che non mi interessano, ma cne mi arrivano vengono persi
            Behaviors.same
        }
      }
    }
  }

  def initialised(position: (Double, Double)): Behavior[Command] = Behaviors.receive {
    case (ctx, MoveTo(x, y)) =>
      ctx.log.info(s"Move to => $x, $y")
      initialised(x, y)
    case (ctx, Stop) => Behaviors.stopped
    case _ => Behaviors.same
  }

@main def checkStash(): Unit =
  val system = ActorSystem(Node(), "node")
  system ! MoveTo(10, 10)
  system ! Stop
  Thread.sleep(1000)
  system ! Start(0, 0)
