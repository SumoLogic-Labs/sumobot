package com.sumologic.sumobot.plugins.beer

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import com.sumologic.sumobot.brain.InMemoryBrain
import com.sumologic.sumobot.core.model.OutgoingMessage
import com.sumologic.sumobot.plugins.BotPlugin.InitializePlugin
import com.sumologic.sumobot.test.BotPluginTestKit
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import scala.concurrent.duration._

class BeerTest
  extends BotPluginTestKit(ActorSystem("BeerTest")) {

  val beerRef = system.actorOf(Props(classOf[Beer]), "beer")
  val brainRef = system.actorOf(Props(classOf[InMemoryBrain]), "brain")
  beerRef ! InitializePlugin(null, beerRef, null)

  "beer" should {
    "return one of specified beer responses" in {
      val otherPlugin = TestProbe()
      system.eventStream.subscribe(otherPlugin.ref, classOf[OutgoingMessage])
      send(instantMessage("Some beer would be great right about now"))
      eventually(Timeout(5.seconds)) {
        val messages = otherPlugin.expectMsgAllClassOf(classOf[OutgoingMessage])
        messages.foreach(msg => println(msg.text))
        messages.exists(m => Beer.BeerPhrases.contains(m.text))
      }
    }
  }
}