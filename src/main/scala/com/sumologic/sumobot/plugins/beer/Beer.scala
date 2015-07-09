package com.sumologic.sumobot.plugins.beer

import com.sumologic.sumobot.plugins.BotPlugin

import scala.util.Random

class Beer extends BotPlugin {

  override protected def name: String = "beer"

  override protected def help: String = "I'll voice my opinion about certain beverages when appropriate."

  private val BeerMention = matchText(".*(beer[s]?).*")

  private val BeerPhrases = List(
    "Ohh, what I wouldn't give for a beer.",
    "I'm getting another beer.",
    "Robots are made out of old beer cans.",
    "Ah, Jeez, let's just pray I have the energy to get myself another beer.",
    "Hmmm... okay, but I'll need ten kegs of beer, a continuous tape of \"Louie, Louie,\" and a regulation two-story panty-raid ladder.",
    "Ah, beer. So many choices, and it makes so little difference.",
    "Hey, that was my last beer! You bastard! I'll kill you!"
  )

  private var lastChimedIn = 0l

  override protected def receiveText: ReceiveText = {
    case BeerMention(beer) =>
      val now = System.currentTimeMillis()
      if (now - lastChimedIn > 60000 && Random.nextInt(10) < 8) {
        lastChimedIn = now
        botMessage.say(chooseRandom(BeerPhrases: _*))
      }
  }
}
