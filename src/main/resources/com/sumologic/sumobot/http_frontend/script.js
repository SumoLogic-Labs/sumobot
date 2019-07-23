/*
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
window.onload = function() {
    var websocketProtocol = window.location.protocol === "https:" ? "wss://" : "ws://";

    var endpoint = websocketProtocol + window.location.host + window.location.pathname + "websocket";

    var socket = new WebSocket(endpoint);
    var messageBox = document.getElementById("messages");

    var submitButton = document.getElementById("submit");
    submitButton.onclick = function() {
        var messageBox = document.getElementById("message");
        socket.send(messageBox.value);
    };

    socket.onmessage = function(event) {
        var messageItem = document.createElement("p");
        messageItem.textContent = event.data;
        messageItem.setAttribute("style", "background: gray; padding: 20px; white-space: pre-line;");
        messageBox.appendChild(messageItem);
    };
};
