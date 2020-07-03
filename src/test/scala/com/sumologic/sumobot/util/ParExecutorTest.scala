package com.sumologic.sumobot.util

import java.util.concurrent.atomic.AtomicInteger

import com.sumologic.sumobot.test.annotated.SumoBotSpec

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
