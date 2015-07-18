package com.sumologic.sumobot.plugins.brain

import akka.pattern._
import akka.util.Timeout
import com.sumologic.sumobot.Receptionist.BotMessage
import com.sumologic.sumobot.brain.Brain.{Remove, Store, ListValues, ValueMap}
import com.sumologic.sumobot.plugins.BotPlugin
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class BrainSurgery extends BotPlugin {
  override protected def name = "brain-surgery"
  override protected def help =
    """Mess with my brain:
      |
      |dump your brain - I'll tell you everything I know.
      |remember: xxx.yyy=zzz - Will make me remember.
      |forget about xxx.yyy - Will make me forget xxx.
    """.stripMargin

  private val brainDump = matchText(".*dump\\s.*brain.*")

  private val remember = matchText("remember[\\s\\:]+([\\.\\w]+)=(\\w+).*")

  private val forget = matchText("forget about ([\\.\\w]+).*")

  override protected def receiveBotMessage = {
    case botMessage@BotMessage(remember(key, value), _, _, _) if botMessage.addressedToUs =>
      brain ! Store(key.trim, value.trim)
      botMessage.respond(s"Got it, $key is $value")
    case botMessage@BotMessage(forget(key), _, _, _) if botMessage.addressedToUs =>
      brain ! Remove(key.trim)
      botMessage.respond(s"$key? I've forgotten all about it.")
    case botMessage@BotMessage(brainDump(), _, _, _) if botMessage.addressedToUs =>
      implicit val timeout = Timeout(5 seconds)
      (brain ? ListValues()) map {
        case ValueMap(map) =>
          if (map.isEmpty) {
            botMessage.say("My brain is empty.")
          } else {
            botMessage.say(map.toSeq.sortBy(_._1).map(tpl => s"${tpl._1}=${tpl._2}").mkString("\n"))
          }
      }
  }
}
