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
package com.sumologic.sumobot.plugins

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.sumologic.sumobot.brain.BlockingBrain
import com.sumologic.sumobot.core.Bootstrap
import com.sumologic.sumobot.core.model._
import com.sumologic.sumobot.plugins.BotPlugin.{InitializePlugin, PluginAdded, PluginRemoved}
import com.sumologic.sumobot.quartz.QuartzExtension
import com.typesafe.config.Config
import org.apache.http.HttpResponse
import org.apache.http.client.methods.{HttpGet, HttpUriRequest}
import slack.models.{Group, Im, User, Channel => ClientChannel}
import slack.rtm.RtmState

import java.net.URLEncoder
import java.util.concurrent.{Executors, TimeoutException}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.{Failure, Success}

object BotPlugin {

  case object RequestHelp

  case class PluginAdded(plugin: ActorRef, help: String)

  case class PluginRemoved(plugin: ActorRef)

  case class InitializePlugin(state: RtmState, brain: ActorRef, pluginRegistry: ActorRef)

  def matchText(regex: String): Regex = ("(?i)(?s)" + regex).r
}

abstract class BotPlugin
  extends Actor
    with ActorLogging
    with Emotions {

  type ReceiveIncomingMessage = PartialFunction[IncomingMessage, Unit]
  type ReceiveReaction = PartialFunction[Reaction, Unit]

  protected var state: RtmState = _

  protected var brain: ActorRef = _

  protected var pluginRegistry: ActorRef = _

  // For plugins to implement.

  protected def receiveIncomingMessage: ReceiveIncomingMessage
  protected def receiveReaction: ReceiveReaction = Map.empty

  protected def help: String

  // Helpers for plugins to use.

  protected def sendMessage(msg: OutgoingMessage): Unit = context.system.eventStream.publish(msg)
  protected def sendMessage(msg: OutgoingMessageWithAttachments): Unit = context.system.eventStream.publish(msg)
  protected def sendImage(im: OutgoingImage): Unit = context.system.eventStream.publish(im)

  protected def responseConcurrency = 10

  protected def responseTimeout = 10.seconds

  implicit protected val responseExecutionContext = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(responseConcurrency))

  class RichIncomingMessage(msg: IncomingMessage) {
    def response(text: String, inThread: Boolean = false) = {
      val threadTs = if (inThread) {
        msg.threadTimestamp.orElse(Some(msg.idTimestamp))
      } else {
        None
      }
      OutgoingMessage(msg.channelId, responsePrefix(inThread) + text, threadTs)
    }

    def message(text: String) = OutgoingMessage(msg.channelId, text)

    def say(text: String) = sendMessage(message(text))

    def respond(text: String, inThread: Boolean = false) = sendMessage(response(text, inThread))

    private def responsePrefix(inThread: Boolean): String =
      if (inThread) {
        ""
      } else {
        s"${msg.sentBy.slackReference}: "
      }

    def scheduleResponse(delay: FiniteDuration, text: String): Unit = scheduleOutgoingMessage(delay, response(text))

    def scheduleMessage(delay: FiniteDuration, text: String): Unit = scheduleOutgoingMessage(delay, message(text))

    def scheduleOutgoingMessage(delay: FiniteDuration, outgoingMessage: OutgoingMessage): Unit = {
      context.system.scheduler.scheduleOnce(delay, new Runnable() {
        override def run(): Unit = sendMessage(outgoingMessage)
      })
    }

    def respondInFuture(body: IncomingMessage => OutgoingMessage): Unit = {
      respondAsync((x: IncomingMessage)  => Future[OutgoingMessage]{body(x)})
    }

    def respondAsync(body: IncomingMessage => Future[OutgoingMessage]): Unit = {
      val timeout = akka.pattern.after(responseTimeout, using = context.system.scheduler)(
        Future.failed[OutgoingMessage](new TimeoutException("Response timed out")))
      val response: Future[OutgoingMessage] = Future.firstCompletedOf[OutgoingMessage](Seq(timeout, body(msg)))
      response.onComplete{
        case Failure(NonFatal(e)) =>
          log.error(e, "Execution failed.")
          msg.response("Execution failed.")
        case Success(message) =>
          sendMessage(message)
      }
    }

    def httpGet(url: String)(func: (IncomingMessage, HttpResponse) => OutgoingMessage): Unit = http(new HttpGet(url))(func)

    def http(request: HttpUriRequest)(func: (IncomingMessage, HttpResponse) => OutgoingMessage): Unit = {
      respondInFuture {
        (incoming: IncomingMessage) =>
          val client = HttpClientWithTimeOut.client()
          func(incoming, client.execute(request))
      }
    }
  }

  implicit def enrichIncomingMessage(msg: IncomingMessage): RichIncomingMessage = new RichIncomingMessage(msg)

  implicit def clientToPublicChannel(channel: ClientChannel): PublicChannel = PublicChannel(channel.id, channel.name)

  implicit def clientToGroupChannel(group: Group): GroupChannel = GroupChannel(group.id, group.name)

  protected val UserId = "<@(\\w+)>"

  protected val ChannelId = "<#(C\\w+)\\|.*>"

  protected def mention(user: User): String = s"<@${user.id}>"

  protected def matchText(regex: String): Regex = BotPlugin.matchText(regex)

  protected def blockingBrain: BlockingBrain = new BlockingBrain(brain)

  protected def urlEncode(string: String): String = URLEncoder.encode(string, "utf-8")

  protected def scheduleActorMessage(name: String, cronExpression: String, message: AnyRef): Unit = {
    QuartzExtension(context.system).scheduleMessage(name, cronExpression, self, message)
  }

  protected def config: Config = context.system.settings.config.getConfig(s"plugins.${self.path.name}")

  // Implementation. Most plugins should not override.

  override final def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[IncomingMessage])
    context.system.eventStream.subscribe(self, classOf[Reaction])
    Bootstrap.receptionist.foreach(_ ! PluginAdded(self, help))
    pluginPreStart()
  }

  protected def pluginPreStart(): Unit = {}

  override final def postStop(): Unit = {
    Bootstrap.receptionist.foreach(_ ! PluginRemoved(self))
    context.system.eventStream.unsubscribe(self)
    pluginPostStop()
  }

  protected def pluginPostStop(): Unit = {}

  private final def receiveIncomingMessageInternal: ReceiveIncomingMessage = receiveIncomingMessage orElse {
    case ignore =>
  }

  private final def receiveReactionInternal: ReceiveReaction = receiveReaction orElse {
    case ignore =>
  }

  override def receive: Receive = uninitialized orElse pluginReceive

  private def uninitialized: Receive = {
    case InitializePlugin(newState, newBrain, newPluginRegistry) =>
      this.state = newState
      this.brain = newBrain
      this.pluginRegistry = newPluginRegistry
      this.initialize()
      context.become(initialized orElse pluginReceive)
  }

  protected final def initialized: Receive = {
    case message@IncomingMessage(text, _, _, _, _, _, _) =>
      receiveIncomingMessageInternal(message)

    case reaction@Reaction(_, _, _, _) =>
      receiveReactionInternal(reaction)
  }

  protected def pluginReceive: Receive = Map.empty

  protected def initialize(): Unit = {}
}

