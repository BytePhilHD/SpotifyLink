window.onload = setupWebSocket;
let ws;
let uri;
let uri1;
let uri2;
let uri3;
let sentValue;
let refreshQueue = false;

let cachedImage;

let cachedImages = {};

let songAdded;

let wrongCode = false;

function hideSearch() {
  for (let i = 1; i <= 3; i++) {
    document.getElementById(`search-${i}-button`).style.visibility = "hidden";
    document.getElementById(`search-${i}-name`).style.visibility = "hidden";
    document.getElementById(`search-${i}-artists`).style.visibility = "hidden";
    document.getElementById(`search-${i}-cover`).style.visibility = "hidden";
  }
}

function setupWebSocket() {
  if (wrongCode) {
    return;
  }
  if (ws) {
    ws.close();
  }

  try {
    if (location.protocol === "http:") {
      ws = new WebSocket(
        "ws://" + location.hostname + ":" + location.port + "/main"
      );
    } else {
      ws = new WebSocket(
        "wss://" + location.hostname + ":" + location.port + "/main"
      );
    }
  } catch (e) {
    setInterval(setupWebSocket, 2000);
  }
  ws.onopen = () => {
    refresh();
  };
  setInterval(refresh, 2000);

  hideSearch();
  ws.onmessage = (messageEvent) => {
    if (messageEvent.data.includes("QUEUE-LENGTH: ")) {
      let queueLength = messageEvent.data.replace("QUEUE-LENGTH: ", "");
      if (queueLength == -1) {
        document.getElementById("song-added").innerHTML =
          "Lied spielt als nächstes.";
        songAdded = false;
        counter = 0;
      } else {
        document.getElementById("song-added").innerHTML =
          "Lied spielt in ca. " + queueLength + " min";
        songAdded = false;
        counter = 0;
      }
      return;
    }
    if (messageEvent.data == "forbidden") {
      wrongCode = true;
      document.getElementById("song-name").innerHTML = "Falscher Code!";
      document.getElementById("song-artists").innerHTML =
        "Seite neuladen um den Code einzugeben.";

      var code = prompt("Gib den aktuellen Session Code ein:", "");

      if (code != null && code != "") {
        location.search = code.toUpperCase();
        return;
      }
      return;
    }
    try {
      let wsinput = JSON.parse(messageEvent.data);

      if (wsinput.type == "search") {
        if (document.querySelector("#searchbar").value != sentValue) {
          let searchResults = wsinput.results;

          for (let i = 1; i <= 3; i++) {
            if (searchResults[i - 1]) {
              let search = searchResults[i - 1];
              document.getElementById(`search-${i}-name`).style.visibility =
                "visible";
              document.getElementById(`search-${i}-name`).innerHTML =
                search.name;
              document.getElementById("song-added").innerHTML = " ";
              document.getElementById(`search-${i}-artists`).style.visibility =
                "visible";
              document.getElementById(`search-${i}-artists`).innerHTML =
                search.artists;
              document.getElementById(`search-${i}-cover`).style.visibility =
                "visible";
              document.getElementById(`search-${i}-cover`).src = search.cover;
              document.getElementById(`search-${i}-button`).style.visibility =
                "visible";
              window[`uri${i}`] = search.uri;
              var button = document.getElementById(`search-${i}-button`);
              if (search.played == true) {
                button.style.backgroundColor = "#FFA500";
                button.style.borderColor = "#FFA500";
              } else {
                button.style.backgroundColor = "";
                button.style.borderColor = "";
              }
            }
          }
        }
      } else if (wsinput.type === "queue") {
        let queueResults = wsinput.results;
        for (let i = 1; i <= 3; i++) {
          if (queueResults[i - 1]) {
            let queue = queueResults[i - 1];
            document.getElementById(`queue-${i}-name`).innerHTML = queue.name;
            document.getElementById(`queue-${i}-artists`).innerHTML =
              queue.artists;
            if (queue.cover !== cachedImages[i]) {
              document.getElementById(`queue-${i}-cover`).src = queue.cover;
              cachedImages[i] = queue.cover;
            }
          }
        }
      } else {
        if (wsinput["name"] !== undefined) {
          document.getElementById("song-name").innerHTML = wsinput["name"];
        }

        if (wsinput["artists"] !== undefined) {
          document.getElementById("song-artists").innerHTML =
            wsinput["artists"];
        }

        if (wsinput["cover"] !== undefined) {
          if (wsinput["cover"] !== cachedImage) {
            document.getElementById("song-cover").src = wsinput["cover"];
            cachedImage = wsinput["cover"];
          }
        }

        if (wsinput["uri"] !== undefined) {
          url = wsinput["uri"];
        }

        if (wsinput["Not-playing"]) {
          document.getElementById("song-name").innerHTML = "Kein Song läuft";
          document.getElementById("song-artists").innerHTML = "...";
        }
      }
    } catch (e) {
      console.error("Error parsing JSON:", e);
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
      data.action = "add-song";
      data.content = window[`uri${i}`];
      ws.send(JSON.stringify(data));
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
    data.action = "search";
    data.content = input.value;
    ws.send(JSON.stringify(data));
  }
}

document.getElementById("queueToggle").onclick = function () {
  refreshQueue = !refreshQueue;
  refresh();
  var content = document.getElementById("queueContent");
  var help = document.getElementById("help");
  var arrow = document.getElementById("queueArrow");
  if (content.style.display === "none") {
    content.style.display = "block";
    help.style.display = "none";
    arrow.innerHTML = "&#9650;";
  } else {
    content.style.display = "none";
    help.style.display = "block";
    arrow.innerHTML = "&#9660;";
  }
};

let data = {
  sessionCode: location.search.replace("?", "").replace("/", ""),
  action: "",
  content: "",
};

let counter = 0;

function refresh() {
  if (wrongCode) {
    return;
  }
  if (ws.readyState == 0) {
    return;
  }
  if (refreshQueue) {
    data.action = "refresh";
    data.content = "queue";
    ws.send(JSON.stringify(data));
  } else {
    data.action = "refresh";
    ws.send(JSON.stringify(data));
  }
  if (input.value !== null && input.value !== "") {
    data.action = "search";
    data.content = input.value;
    ws.send(JSON.stringify(data));
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
