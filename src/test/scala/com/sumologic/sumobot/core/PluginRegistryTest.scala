/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sumologic.sumobot.core

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.sumologic.sumobot.core.PluginRegistry.{Plugin, PluginList, RequestPluginList}
import com.sumologic.sumobot.plugins.BotPlugin.{PluginAdded, PluginRemoved}
import com.sumologic.sumobot.plugins.help.Help
import com.sumologic.sumobot.test.annotated.SumoBotTestKit
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration._

class PluginRegistryTest
  extends SumoBotTestKit(ActorSystem("PluginRegistryTest"))
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
    TestKit.shutdownActorSystem(system)
  }
}
