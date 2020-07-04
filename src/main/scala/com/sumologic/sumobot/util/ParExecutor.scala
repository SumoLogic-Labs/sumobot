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
