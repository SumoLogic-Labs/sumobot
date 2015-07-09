package com.sumologic.sumobot

import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually

/**
 * @author Chris (chris@sumologic.com)
 */
trait SumoBotSpec extends WordSpecLike with Eventually with Matchers
