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
package com.sumologic.sumobot.plugins.alias

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import com.sumologic.sumobot.brain.InMemoryBrain
import com.sumologic.sumobot.core.model.IncomingMessage
import com.sumologic.sumobot.plugins.BotPlugin.InitializePlugin
import com.sumologic.sumobot.test.annotated.BotPluginTestKit
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import scala.concurrent.duration._
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually._

class AliasTest
  extends BotPluginTestKit(ActorSystem("AliasTest")) {

  val aliasRef = system.actorOf(Props(classOf[Alias]), "alias")
  val brainRef = system.actorOf(Props(classOf[InMemoryBrain]), "brain")
  aliasRef ! InitializePlugin(null, brainRef, null)

  "alias" should {
    "allow aliasing messages to the bot" in {
      send(instantMessage("alias 'foo' to 'bar'"))
      val otherPlugin = TestProbe()
      system.eventStream.subscribe(otherPlugin.ref, classOf[IncomingMessage])
      send(instantMessage("foo"))
      eventually(Timeout(5.seconds)) {
        val messages = otherPlugin.expectMsgAllClassOf(classOf[IncomingMessage])
        messages.foreach(msg => println(msg.canonicalText))
        messages.exists(_.canonicalText == "bar") should be (true)
      }
    }
  }
}
