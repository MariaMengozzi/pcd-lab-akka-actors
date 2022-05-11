package it.unibo.pcd.akka.basics.e01hello

import akka.actor.typed.{ActorSystem, Behavior, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import Counter.*

// "Actor" module definition
object Counter: //contiene la definizione dell'attore, quindi l'insieme di messaggi che può gestire, in questo caso solo il msg di tipo Tick
  enum Command: // APIs i.e. message that actors should received / send. pu o anche essere creato con delle case class
    case Tick
  export Command.*
  def apply(from: Int, to: Int): Behavior[Command] = //tipicamente è una factory quindi si usa la apply

    /*sl posto di creare il behaviour usiamo uno stato per l'attore, (alternativa da non usare, meglio preferire quella non commentata)
      quindi in questo caso non ce lo portiamo semore dietro lo possiamo fare mediante il carring
    può essere utile quando abbiamo una gui e c'è un oggetto mutabile, infatti mediante questo modo riusciamo a mantenere le API funzionale
    * var count = from
    Behaviors.receive { (context, msg) =>
      /*crea un behaviour per gestire il messaggio che gli viene passato in input*/
      msg match {
        case Tick if count != to => //from è andato nel behaviour successivo.
          context.log.info(s"Count: $from")
          count -= from.compareTo(to)
          Behaviour.same
        case _ => Behaviors.stopped //quando from arrivo a to mi fermo
      }
    }
    * */

    Behaviors.receive { (context, msg) =>
      /*crea un behaviour per gestire il messaggio che gli viene passato in input*/
      msg match {
        case Tick if from != to => //from è andato nel behaviour successivo.
          context.log.info(s"Count: $from")
          Counter(from - from.compareTo(to), to)
        case _ => Behaviors.stopped //quando from arrivo a to mi fermo
      }
    }
  def apply(to: Int): Behavior[Command] =
    Behaviors.setup(new Counter(_, 0, to))

class Counter(context: ActorContext[Counter.Command], var from: Int, val to: Int)
    extends AbstractBehavior[Counter.Command](context):
  override def onMessage(msg: Counter.Command): Behavior[Counter.Command] = msg match {
    case Tick if from != to =>
      context.log.info(s"Count: $from")
      from -= from.compareTo(to)
      this
    case _ => Behaviors.stopped
  }
@main def functionalApi: Unit =
  val system = ActorSystem[Command](Counter(0, 2), "counter") //creazione dell'attore.
  for (i <- 0 to 2) system ! Tick
  //system.terminate() //è un metodo alternativo per chiudere l'actorSystem, va a sostituire il .stopped per il tutti gli scenari che non matchano

@main def OOPApi: Unit = //meglio usare questa rispetto a quella funzionale quando abbiamo uno stato
  val system = ActorSystem[Counter.Command](Counter(2), "counter")
  for (i <- 0 to 2) system ! Tick
