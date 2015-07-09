package com.sumologic.sumobot

import com.sumologic.sumobot.plugins.BotPlugin
import org.scalatest.{Matchers, WordSpec}

class RegexPlaygroundTest extends SumoBotSpec {
  "BotPlugin.matchText()" should {
    "be case insensitive" in {
      val testRegex = BotPlugin.matchText("hello")
      "hello" should fullyMatch regex testRegex
      "Hello" should fullyMatch regex testRegex
      "HellO" should fullyMatch regex testRegex
    }

    "support or via pipe" in {
      val testRegex = BotPlugin.matchText("(hello|world)")
      "hello" should fullyMatch regex testRegex
      "world" should fullyMatch regex testRegex
    }

    "extract stuff between pipes" in {
      val testRegex = BotPlugin.matchText("hello (\\w+) world!")
      "Hello beautiful world!" match {
        case testRegex(kind) => kind should be ("beautiful")
        case _ => fail("Did not match!")
      }
    }

    "extract stuff between pipes with or" in {
      val testRegex = BotPlugin.matchText("hello (\\d+|beautiful) world!")
      "Hello beautiful world!" match {
        case testRegex(kind) => kind should be ("beautiful")
        case _ => fail("Did not match!")
      }
      "Hello 123 world!" match {
        case testRegex(kind) => kind should be ("123")
        case _ => fail("Did not match!")
      }
      "Hello NOT world!" match {
        case testRegex(kind) => fail("Should not match!")
        case _ =>
      }
    }

    "support conditional :" in {
      val testRegex = BotPlugin.matchText("tell <@(\\w+)>[:]?\\s*(.*)")

      "tell <@ABCDEF> blah" match {
        case testRegex(_, text) => text should be ("blah")
        case _ => fail("Did not match!")
      }

      "tell <@ABCDEF>: blah" match {
        case testRegex(_, text) => text should be ("blah")
        case _ => fail("Did not match!")
      }
    }
  }
}
