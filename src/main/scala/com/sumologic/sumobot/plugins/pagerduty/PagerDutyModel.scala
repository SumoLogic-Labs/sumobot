package com.sumologic.sumobot.plugins.pagerduty

case class PagerDutyEscalationPolicies(escalation_policies: List[PagerDutyEscalationPolicy])

case class PagerDutyEscalationPolicy(id: String,
                                     name: String,
                                     on_call: List[PagerDutyOnCall])

case class PagerDutyOnCall(level: Int,
                           user: PagerDutyOnCallUser)

case class PagerDutyOnCallUser(name: String, email: String)
