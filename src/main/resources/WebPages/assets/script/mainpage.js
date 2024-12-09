window.onload = setupWebSocket;
let ws;
let uri;
let uri1;
let uri2;
let uri3;
let sentValue;

let songAdded;

function hideSearch() {
  for (let i = 1; i <= 3; i++) {
    document.getElementById(`search-${i}-button`).style.visibility = "hidden";
    document.getElementById(`search-${i}-name`).style.visibility = "hidden";
    document.getElementById(`search-${i}-artists`).style.visibility = "hidden";
    document.getElementById(`search-${i}-cover`).style.visibility = "hidden";
  }
}

function setupWebSocket() {
  if (location.protocol === "http:") {
    ws = new WebSocket(
      "ws://" + location.hostname + ":" + location.port + "/main"
    );
  } else {
    ws = new WebSocket(
      "wss://" + location.hostname + ":" + location.port + "/main"
    );
  }

  setInterval(refresh, 2000);

  hideSearch();
  ws.onmessage = (messageEvent) => {
    if (messageEvent.data.includes("QUEUE-LENGTH: ")) {
      let queueLength = messageEvent.data.replace("QUEUE-LENGTH: ", "");
      if (queueLength == -1) {
        document.getElementById("song-added").innerHTML =
          "Fehler beim Hinzufügen des Songs!";
      } else {
        document.getElementById("song-added").innerHTML =
          "Lied spielt in ca. " + queueLength + " min";
        songAdded = false;
      }
      return;
    }
    let wsinput = JSON.parse(messageEvent.data);

    if (wsinput["name"] !== undefined) {
      document.getElementById("song-name").innerHTML = wsinput["name"];
    }

    if (wsinput["artists"] !== undefined) {
      document.getElementById("song-artists").innerHTML = wsinput["artists"];
    }

    if (wsinput["cover"] !== undefined) {
      document.getElementById("song-cover").src = wsinput["cover"];
    }

    if (wsinput["uri"] !== undefined) {
      url = wsinput["uri"];
    }

    if (wsinput["Not-playing"]) {
      document.getElementById("song-name").innerHTML = "Kein Song läuft";
      document.getElementById("song-artists").innerHTML = "...";
    }
    if (document.querySelector("#searchbar").value != sentValue) {
      for (let i = 1; i <= 3; i++) {
        if (wsinput.hasOwnProperty(`search-${i}`)) {
          let search = wsinput[`search-${i}`];
          document.getElementById(`search-${i}-name`).style.visibility =
            "visible";
          document.getElementById(`search-${i}-name`).innerHTML =
            search["name"];
          document.getElementById("song-added").innerHTML = " ";
          document.getElementById(`search-${i}-artists`).style.visibility =
            "visible";
          document.getElementById(`search-${i}-artists`).innerHTML =
            search["artists"];
          document.getElementById(`search-${i}-cover`).style.visibility =
            "visible";
          document.getElementById(`search-${i}-cover`).src = search["cover"];
          document.getElementById(`search-${i}-button`).style.visibility =
            "visible";
          window[`uri${i}`] = search["uri"];
          var button = document.getElementById(`search-${i}-button`);
          if (search["played"] == true) {
            button.style.backgroundColor = "#FFA500";
            button.style.borderColor = "#FFA500";
          } else {
            button.style.backgroundColor = "";
            button.style.borderColor = "";
          }
        }
      }

      for (let i = 1; i <= 3; i++) {
        if (wsinput.hasOwnProperty(`queue-${i}`)) {
          let queue = wsinput[`queue-${i}`];
          document.getElementById(`queue-${i}-name`).innerHTML = queue["name"];
          document.getElementById(`queue-${i}-artists`).innerHTML =
            queue["artists"];
          document.getElementById(`queue-${i}-cover`).src = queue["cover"];
        }
      }
    }
  };
  ws.onclose = (closeEvent) => {
    if (ws.readyState == 0) {
      return;
    } else {
      ws = null;
      setupWebSocket();
    }
  };

  // Buttons for selecting the right song
  for (let i = 1; i <= 3; i++) {
    document.getElementById(`search-${i}-button`).onclick = function () {
      ws.send("Song-Play: " + window[`uri${i}`]);
      hideSearch();
      document.getElementById("song-added").innerHTML =
        "Song wird hinzugefügt...";
      sentValue = document.querySelector("#searchbar").value;
      songAdded = true;
    };
  }

  document.getElementById("song-cover").onclick = function () {
    location.href = url;
  };
}

const input = document.querySelector("#searchbar");

input.addEventListener("change", updateValue);

function updateValue() {
  if (input.value !== null && input.value !== "") {
    ws.send("Search: " + input.value);
  }
}

// refresh function which gets timed every 2000 ms (on the top)
let counter = 0;

function refresh() {
  if (ws.readyState == 0) {
    return;
  }
  ws.send("refresh");
  if (input.value !== null && input.value !== "") {
    ws.send("Search: " + input.value);
  }
  if (songAdded) {
    if (counter == 3) {
      document.getElementById("song-added").innerHTML =
        "Fehler beim Hinzufügen des Songs!";
      counter = 0;
      songAdded = false;
      return;
    } else {
      counter++;
    }
  }
}
