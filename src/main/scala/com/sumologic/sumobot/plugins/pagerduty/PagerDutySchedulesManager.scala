package com.sumologic.sumobot.plugins.pagerduty

import com.netflix.config.scala.DynamicStringProperty
import net.liftweb.json._
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient

object PagerDutySchedulesManager {
  def createClient(): Option[PagerDutySchedulesManager] = {
    for (token <- DynamicStringProperty("pagerduty.token", null)();
         pagerDutyUrl <- DynamicStringProperty("pagerduty.url", null)())
      yield new PagerDutySchedulesManager(token, pagerDutyUrl)
  }
}

class PagerDutySchedulesManager(token: String, pagerdutyUrl: String) {

  def getEscalationPolicies: Option[PagerDutyEscalationPolicies] = {

    def liftJson(str: String): PagerDutyEscalationPolicies = {
      val json = JsonParser.parse(str)

      val policies = for {
        JArray(policies) <- json \\ "escalation_policies"
        JObject(policy) <- policies
        JField("id", JString(id)) <- policy
        JField("name", JString(name)) <- policy
        JField("on_call", JArray(on_calls)) <- policy
        calls: List[PagerDutyOnCall] = on_calls.map(call => {
          implicit val formats = DefaultFormats.withHints(ShortTypeHints(List(classOf[PagerDutyOnCall],
            classOf[PagerDutyOnCallUser])))

          call.extract[PagerDutyOnCall]

        })
      } yield PagerDutyEscalationPolicy(id, name, calls)

      PagerDutyEscalationPolicies(policies)
    }

    getPagerDutyJson("escalation_policies/on_call").map(liftJson)
  }

  private def getPagerDutyJson(command: String): Option[String] = {
    val client = new DefaultHttpClient()
    try {

      val url = s"$pagerdutyUrl/api/v1/$command"
      val getReq = new HttpGet(url)

      getReq.addHeader("content-type", "application/json")
      getReq.addHeader("Authorization", s"Token token=$token")

      val response = client.execute(getReq)
      val entity = response.getEntity


      if (entity != null) {
        val inputStream = entity.getContent
        Some(scala.io.Source.fromInputStream(inputStream).mkString)
      } else {
        None
      }
    } catch {
      case e: Exception =>
        println(e)
        None
    } finally {
      client.getConnectionManager.shutdown()
    }
  }
}
