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
package com.sumologic.sumobot.util

import com.sumologic.sumobot.test.annotated.SumoBotSpec

import java.util.concurrent.atomic.AtomicInteger

class ParExecutorTest extends SumoBotSpec {

  "ParExecutor.runInParallel" should {
    "execute stuff and eventually reach a conclusion" in {
      val counter = new AtomicInteger(0)
      ParExecutor.runInParallel(Seq("a", "b", "c")) {
        _ => counter.getAndIncrement()
      }
      counter.get() should be(3)
    }
  }

  "ParExecutor.runInParallelAndGetResults" should {
    "execute stuff and return results in order" in {
      ParExecutor.runInParallelAndGetResults(Seq('a', 'b', 'c')) {
        i => (i + 3).toChar
      } should be(Seq('d', 'e', 'f'))
    }
  }

  "ParExecutor.runInParallelAndGetFlattenedResults" should {
    "execute stuff and eventually reach a conclusion" in {
      ParExecutor.runInParallelAndGetFlattenedResults(Seq('a', 'd', 'g')) {
        i => Seq(i, (i + 1).toChar, (i + 2).toChar)
      } should be(Seq('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'))
    }
  }

}
