package com.sumologic.sumobot.plugins.pagerduty

import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.DefaultHttpClient
import play.api.libs.json.Json


class PagerDutyEventApi {

  case class PagerDutyEvent(service_key: String, event_type: String, incident_key: String, description: String)

  implicit val pagerDutyEventFormat = Json.format[PagerDutyEvent]

  private val SubmitUrl = "https://events.pagerduty.com/generic/2010-04-15/create_event.json"

  private val client = new DefaultHttpClient()

  def page(channel: String,
           serviceKey: String,
           description: String) {
    val event = Json.toJson(PagerDutyEvent(serviceKey, "trigger", channel, description))
    val entity = new StringEntity(event.toString(), ContentType.APPLICATION_JSON)
    val post = new HttpPost(SubmitUrl)
    post.setEntity(entity)
    client.execute(post)
  }
}
