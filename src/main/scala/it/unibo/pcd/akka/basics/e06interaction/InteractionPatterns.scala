package it.unibo.pcd.akka.basics.e06interaction

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Success

object HelloBehavior:
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])
  def apply(): Behavior[Greet] = Behaviors.receive { (context, message) =>
    context.log.info("Hello {}!", message.whom)
    message.replyTo ! Greeted(message.whom, context.self) //rispondo al mittente prendendo il suo riferimento
    Behaviors.same
  }

object InteractionPatternsAsk extends App:
  import HelloBehavior.*

  val system = ActorSystem(
    Behaviors.setup[Greeted] { ctx =>
      val greeter = ctx.spawnAnonymous(HelloBehavior())
      given Timeout = 2.seconds
      given Scheduler = ctx.system.scheduler
      given ExecutionContext = ctx.executionContext
      //per attivare ? devo avere nello stesso contesto definiti i 3 given sopra
      val f: Future[Greeted] = greeter ? (replyTo => Greet("Bob", replyTo)) // ? ascks for the future
      //mi permette di rimanere in attesa solo per il tempo definito dal timeout, se non risponde in tempo inizio a fare altro
      f.onComplete {
        //possiamo esplorare il valore con la callback. non sono quindi delle promises (non chiamo la complete),
        // ma posso solo reagire con degli handler
        case Success(Greeted(who, from)) => println(s"$who has been greeted by ${from.path}!") //viene chiamata solo se il messaggio viene ricevuto entro i 2 sec
        case _ => println("No greet")
      }
      Behaviors.empty
    },
    name = "hello-world"
  )

object InteractionPatternsPipeToSelf extends App:
  //quando la future viene completata viene creato un messaggio che gestirò nell'handler principale
  import HelloBehavior._

  val system = ActorSystem(
    Behaviors.setup[Greeted] { ctx =>
      //! imp con le future è importante utilizzare il contesto
      val greeter = ctx.spawn(HelloBehavior(), "greeter")
      given Timeout = 2.seconds
      given Scheduler = ctx.system.scheduler
      val f: Future[Greeted] = greeter ? (replyTo => Greet("Bob", replyTo))
      ctx.pipeToSelf(f)(_.getOrElse(Greeted("nobody", ctx.system.ignoreRef))) //mi mando un messaggio con il risultato ottenuto dalla future
      //in questo modo è l'handler principale che gestisce il messaggio
      Behaviors.receive { case (ctx, Greeted(whom, from)) =>
        ctx.log.info(s"$whom has been greeted by ${from.path.name}")
        Behaviors.stopped
      }
    },
    name = "hello-world"
  )

object InteractionPatternsSelfMessage extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      Behaviors.withTimers { timers => //è utile quando abbiamo dei comportamenti periodici, o cambiare behaviour ogni tot
        //creo quindi degli attori che mi richiameranno dopo che è passato un tot di tempo
        Behaviors.receiveMessage {
          case "" => Behaviors.stopped
          case s =>
            //ogni tot stampo la testa, fino a quando non è vuota, che mi frmerò
            ctx.log.info("" + s.head)
            timers.startSingleTimer(s.tail, 300.millis)
            Behaviors.same
        }
      }
    },
    name = "hello-world"
  )

  system ! "hello akka"

object InteractionPatternsMsgAdapter extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      //adatto il riferimento a gestire degli interi, facendo si che quando ricevo un int lo trasformo in stringa
      val adaptedRef: ActorRef[Int] = ctx.messageAdapter[Int](i => if (i == 0) "" else i.toString)
      /*l'adapter è imp quando ho un actor ref che gestisce alcuni messaggi e vuole interagire con altri che ne gestiscono di diversi*/
      adaptedRef ! 130
      adaptedRef ! 0
      Behaviors.receiveMessage {
        case "" =>
          ctx.log.info("Bye bye")
          Behaviors.stopped
        case s =>
          ctx.log.info(s)
          Behaviors.same
      }
    },
    name = "hello-world"
  )

  system ! "hello akka"
