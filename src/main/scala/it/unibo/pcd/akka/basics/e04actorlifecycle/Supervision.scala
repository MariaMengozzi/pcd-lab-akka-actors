package it.unibo.pcd.akka.basics.e04actorlifecycle

import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy, Terminated}
import akka.actor.typed.scaladsl.Behaviors

object SupervisedActor:
  def apply(supervisorStrategy: SupervisorStrategy): Behavior[String] = Behaviors
    .supervise[String](actor(""))
    .onFailure[RuntimeException](supervisorStrategy)

  def actor(prefix: String): Behavior[String] = Behaviors.receive {
    case (ctx, "fail") => throw new RuntimeException("Just fail") //dovrà essere gestita dall'attore padre
    case (ctx, "quit") =>
      ctx.log.info("Quitting")
      ctx.log.info(s"Bye!! $prefix")
      Behaviors.stopped
    case (ctx, s) =>
      ctx.log.info(s"Got ${prefix + s}")
      actor(prefix + s)
  }
//esempi di gestione delle eccezioni (diverse policies)
object SupervisionExampleRestart extends App:
  //con la policy di restart lui dopo l'eccezione, quindi muore, l'attore riparte
  // nota questo sistema non mantiene lo stato collezionato prima di morire
  val system = ActorSystem[String](SupervisedActor(SupervisorStrategy.restart), "supervision")
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd

object SupervisionExampleResume extends App:
  //come la restart, ma permette di mantenere lo stato precedente alla morte anche dopo la ripartenza
  val system = ActorSystem[String](SupervisedActor(SupervisorStrategy.resume), "supervision")
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd

object SupervisionExampleStop extends App:
  //con lo stop non vengono più ricevuti e quindi andranno persi
  val system = ActorSystem[String](SupervisedActor(SupervisorStrategy.stop), "supervision")
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd

object SupervisionExampleParent extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      //se non osservo mio figlio anche se lui muore, io continuo a vivere (io = padre)
      val child = ctx.spawn(SupervisedActor(SupervisorStrategy.stop), "fallibleChild")
      Behaviors.receiveMessage { msg =>
        child ! msg
        Behaviors.same
      }
    },
    "supervision"
  )
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd

object SupervisionExampleParentWatching extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      val child = ctx.spawn(SupervisedActor(SupervisorStrategy.stop), "fallibleChild")
      //il padre gestisce il messaggio di errore, per farlo devo isservare i miei figli, mediante la whatch
      ctx.watch(child) // watching child (if Terminated not handled => dead pact)
      Behaviors.receiveMessage[String] { msg =>
        child ! msg
        Behaviors.same
      }
    },
    "supervision"
  )
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd

object SupervisionExampleParentWatchingHandled extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      val child = ctx.spawn(SupervisedActor(SupervisorStrategy.stop), "fallibleChild")
      ctx.watch(child)
      Behaviors
        .receiveMessage[String] { msg =>
          child ! msg
          Behaviors.same
        }
        .receiveSignal { case (ctx, Terminated(ref)) =>
          //mi permette di gestire il fallimento dei miei figli (quindi posso osservare il figlio morto)
          //gli altri messaggi non verranno ricevuti
          ctx.log.info(s"Child ${ref.path} terminated")
          Behaviors.ignore
        }
    },
    "supervision"
  )
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd
