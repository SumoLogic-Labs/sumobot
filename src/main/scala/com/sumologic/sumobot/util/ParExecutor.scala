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

import java.util.concurrent.{SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * This attempts to be a trivial replacement to Scala 2.13 moving .par to a new package, which ends up being hard to
 * import correctly with Gradle.  (It should be relatively easy with SBT, but alas.)
 *
 * See https://github.com/scala/scala-parallel-collections and
 * https://github.com/scala/scala-parallel-collections/issues/22
 */
object ParExecutor {
  implicit private[this] val executor = ExecutionContext.fromExecutor(
    new ThreadPoolExecutor(16, 32, 1000L, TimeUnit.MILLISECONDS, new SynchronousQueue[Runnable]())
  )

  def runInParallel[T](items: Seq[T])(f: T => Unit): Unit = {
    executeInThreadPool(items)(f)
  }

  def runInParallelAndGetResults[T, U](items: Seq[T])(f: T => U): Seq[U] = {
    executeInThreadPool(items)(f)
  }

  def runInParallelAndGetFlattenedResults[T, U](items: Seq[T])(f: T => Seq[U]): Seq[U] = {
    executeInThreadPool(items)(f).flatten
  }

  private[this] def executeInThreadPool[T, U, V](items: Seq[T])(f: T => U): Seq[U] = {
    val futures = items.map {
      item =>
        Future[U] {
          f(item)
        }
    }

    val seqFuture = Future.sequence(futures)
    Await.result(seqFuture, Duration.Inf)
  }

}
