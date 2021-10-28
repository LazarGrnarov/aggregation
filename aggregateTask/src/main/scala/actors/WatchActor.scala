package actors

import akka.actor.{Actor, ActorRef, Terminated}

class WatchActor extends Actor {

  var replyTo = Actor.noSender

  override def receive: Receive = {
    case actor: ActorRef =>
      replyTo = sender()
      context.watch(actor)
    case _: Terminated =>
      replyTo ! true
  }
}
