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
package com.sumologic.sumobot.plugins.jira

import com.sumologic.sumobot.test.SumoBotSpec

class JiraTest extends SumoBotSpec {

  "match jira issue numbers" in {
    "jira proj-123" should fullyMatch regex Jira.JiraInfo
    "jira PROJ-123" should fullyMatch regex Jira.JiraInfo
    "jira PROJ-123     " should fullyMatch regex Jira.JiraInfo
    "jira something" should not (fullyMatch regex Jira.JiraInfo)
    "jira something else" should not (fullyMatch regex Jira.JiraInfo)
    "jira PROJ-1234 else" should not (fullyMatch regex Jira.JiraInfo)
  }
}
