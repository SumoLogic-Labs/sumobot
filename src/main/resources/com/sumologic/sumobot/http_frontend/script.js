window.onload = function() {
    var endpoint = "ws://" + window.location.host + "/websocket";

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
        messageItem.setAttribute("style", "background: gray; padding: 20px; white-space: pre-line;")
        messageBox.appendChild(messageItem);
    };
};
