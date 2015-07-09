package com.sumologic.sumobot

import scala.util.matching.Regex

/**
 * @author Chris (chris@sumologic.com)
 */
trait MatchTextUtil {
  this : SumoBotSpec =>

  def shouldMatch(regex: Regex, text: String): Unit = {
    if (!doesMatch(regex, text)) {
      fail(s"$regex did not match $text but should")
    }
  }

  def shouldNotMatch(regex: Regex, text: String): Unit = {
    if (doesMatch(regex, text)) {
      fail(s"$regex matched $text but should not")
    }
  }

  private def doesMatch(regex: Regex, text: String): Boolean = {
    regex.pattern.matcher(text).find()
  }

}
