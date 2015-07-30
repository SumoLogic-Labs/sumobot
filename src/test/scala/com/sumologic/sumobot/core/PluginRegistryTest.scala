package com.sumologic.sumobot.core

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.sumologic.sumobot.core.PluginRegistry.{Plugin, PluginList, RequestPluginList}
import com.sumologic.sumobot.plugins.BotPlugin.{PluginAdded, PluginRemoved}
import com.sumologic.sumobot.plugins.help.Help
import com.sumologic.sumobot.test.SumoBotSpec
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration._

class PluginRegistryTest
  extends TestKit(ActorSystem("PluginRegistryTest"))
  with SumoBotSpec
  with BeforeAndAfterAll {

  "PluginRegistry" should {
    "maintain a list of all registered plugins" in {


      implicit val timeout = Timeout(1.second)
      val reg = system.actorOf(Props[PluginRegistry])
      def checkList(func: Seq[Plugin] => Unit) = {
        Await.result(reg ? RequestPluginList, 1.second) match {
          case PluginList(list) => func(list)
          case other => fail(s"Got $other instead.")
        }
      }

      val fakePlugin = system.actorOf(Props[Help])

      checkList(_.isEmpty should be(true))
      reg ! PluginAdded(fakePlugin, "hah")
      checkList(_.size should be(1))
      reg ! PluginRemoved(fakePlugin)
      checkList(_.isEmpty should be(true))
    }
  }


  override protected def afterAll(): Unit = {
    system.shutdown()
  }
}
