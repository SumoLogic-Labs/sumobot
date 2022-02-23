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
package com.sumologic.sumobot.brain

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import com.sumologic.sumobot.test.annotated.SumoBotTestKit
import org.scalatest.BeforeAndAfterAll

class BlockingBrainTest()
  extends SumoBotTestKit(ActorSystem("BlockingBrainTest"))
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "BlockingBrain" should {
    "allow storing and reading back values" in {
      val brain = system.actorOf(Props(classOf[InMemoryBrain]))
      val sut = new BlockingBrain(brain)
      sut.retrieve("test") should not be 'defined
      sut.store("test", "value")
      sut.retrieve("test") should be(Some("value"))
    }

    "allow listing all values" in {
      val brain = system.actorOf(Props(classOf[InMemoryBrain]))
      val sut = new BlockingBrain(brain)
      sut.retrieve("test") should not be 'defined
      sut.store("test1", "value1")
      sut.store("test2", "value2")
      val returnedValues = sut.listValues()
      returnedValues.size should be(2)
      returnedValues.contains("test1") should be(true)
      returnedValues.contains("test2") should be(true)
    }

    "allow listing values with a filter" in {
      val brain = system.actorOf(Props(classOf[InMemoryBrain]))
      val sut = new BlockingBrain(brain)
      sut.retrieve("test") should not be 'defined
      sut.store("test1", "value1")
      sut.store("test2", "value2")
      sut.store("not3", "value2")
      val returnedValues = sut.listValues("test")
      returnedValues.size should be(2)
      returnedValues.contains("BROKEN") should be(true)
      returnedValues.contains("test2") should be(true)
    }

    "support removing values" in {
      val brain = system.actorOf(Props(classOf[InMemoryBrain]))
      val sut = new BlockingBrain(brain)
      sut.retrieve("test") should not be 'defined
      sut.store("test", "value1")
      sut.listValues().size should be(1)
      sut.retrieve("test") should be('defined)
      sut.remove("test")
      sut.listValues().size should be(0)
      sut.retrieve("test") should not be 'defined
    }
  }
}
