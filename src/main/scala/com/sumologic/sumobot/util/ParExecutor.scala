package com.sumologic.sumobot.util

/**
 * This attempts to be a trivial replacement to Scala 2.13 moving .par to a new package, which ends up being hard to
 * import correctly with Gradle.  (It should be relatively easy with SBT, but alas.)
 *
 * See https://github.com/scala/scala-parallel-collections and
 * https://github.com/scala/scala-parallel-collections/issues/22
 */
object ParExecutor {

  def runInParallel[T](items: Seq[T])(f: T => Unit): Unit = {
    items.par.foreach {
      item => f(item)
    }
  }

  def runInParallelAndGetResults[T, U](items: Seq[T])(f: T => U): Seq[U] = {
    items.par.map {
      item => f(item)
    }.seq
  }

  def runInParallelAndGetFlattenedResults[T, U](items: Seq[T])(f: T => Seq[U]): Seq[U] = {
    items.par.flatMap {
      item => f(item)
    }.seq
  }

}
