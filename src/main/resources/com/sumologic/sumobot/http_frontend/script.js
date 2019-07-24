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
    var messageInput = document.getElementById("message-input");
    var submitButton = document.getElementById("submit");

    function appendMessage(msg, styles) {
        var messageItem = document.createElement("div");
        messageItem.textContent = msg;
        messageItem.setAttribute("class", styles);
        messageBox.appendChild(messageItem);
        messageItem.scrollIntoView({block: "end", inline: "nearest", behavior: "smooth"});
    }

    function sendMessage() {
        socket.send(messageInput.value);
        appendMessage(messageInput.value, "message outcoming");
        messageInput.value = "";
    }

    submitButton.onclick = function() {
        sendMessage();
    };

    messageInput.onkeydown = function(event) {
        if (event.key === 'Enter') {
            sendMessage();
        }
    }

    socket.onmessage = function(event) {
        appendMessage(event.data, "message incoming");
    };

    window.onbeforeunload = function() {
        socket.close();
    };
};
