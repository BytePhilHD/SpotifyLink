window.onload = init;

let ws;
let url;
let uri1;
let uri2;
let uri3;
let sentValue;
let queueOpen = false;

let cachedImage;

let cachedImages = {};

let wrongCode = false;

// A single reconnect timer, so a flaky connection cannot stack up timers.
let reconnectTimer = null;
let reconnectDelay = 1000;
const MAX_RECONNECT_DELAY = 15000;

let searchDebounceTimer = null;
let addSongTimer = null;
const SEARCH_DEBOUNCE_MS = 250;
const ADD_SONG_TIMEOUT_MS = 8000;

function hideSearch() {
  for (let i = 1; i <= 3; i++) {
    document.getElementById(`search-${i}-button`).style.visibility = "hidden";
    document.getElementById(`search-${i}-name`).style.visibility = "hidden";
    document.getElementById(`search-${i}-artists`).style.visibility = "hidden";
    document.getElementById(`search-${i}-albumImageUrl`).style.visibility = "hidden";
  }
}

// Runs once the page is fully loaded, because the search entries are built by an
// inline script that runs after this file.
function init() {
  hideSearch();

  for (let i = 1; i <= 3; i++) {
    document.getElementById(`search-${i}-button`).onclick = function () {
      if (!window[`uri${i}`] || !send("add-song", window[`uri${i}`])) {
        return;
      }
      hideSearch();
      markSongAdded();
      sentValue = document.querySelector("#searchbar").value;
    };
  }

  document.getElementById("song-albumImageUrl").onclick = function () {
    if (url) {
      location.href = url;
    }
  };

  setupWebSocket();
}

function setupWebSocket() {
  if (wrongCode) {
    return;
  }
  clearTimeout(reconnectTimer);
  reconnectTimer = null;

  const protocol = location.protocol === "http:" ? "ws://" : "wss://";
  try {
    ws = new WebSocket(protocol + location.hostname + ":" + location.port + "/main");
  } catch (e) {
    scheduleReconnect();
    return;
  }

  ws.onopen = () => {
    reconnectDelay = 1000;
    // Ask for the current state once. Everything after that is pushed by the server.
    send("refresh");
    if (queueOpen) {
      send("queue-subscribe", "on");
    }
    if (input.value) {
      sendSearch();
    }
  };

  ws.onmessage = handleMessage;

  ws.onclose = () => {
    scheduleReconnect();
  };

  ws.onerror = () => {
    // onclose follows and takes care of reconnecting.
  };
}

function scheduleReconnect() {
  if (wrongCode || reconnectTimer) {
    return;
  }
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    setupWebSocket();
  }, reconnectDelay);
  reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
}

function handleMessage(messageEvent) {
  if (messageEvent.data.includes("QUEUE-LENGTH: ")) {
    clearTimeout(addSongTimer);
    addSongTimer = null;

    let queueLength = messageEvent.data.replace("QUEUE-LENGTH: ", "");
    if (queueLength == -1) {
      document.getElementById("song-added").innerHTML =
        "Warteschlange wird aktualisiert...";
    } else {
      document.getElementById("song-added").innerHTML =
        "Lied spielt in ca. " + queueLength + " min";
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

    if (wsinput.type == "played-update") {
      // Somebody requested a song, so the markers on open search results are stale.
      if (input.value && input.value !== sentValue) {
        sendSearch();
      }
    } else if (wsinput.type == "search") {
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
            document.getElementById(`search-${i}-albumImageUrl`).style.visibility =
              "visible";
            document.getElementById(`search-${i}-albumImageUrl`).src = search.albumImageUrl;
            document.getElementById(`search-${i}-button`).style.visibility =
              "visible";
            window[`uri${i}`] = search.uri;
            var button = document.getElementById(`search-${i}-button`);
            if (search.inQueue == true) {
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
          if (queue.albumImageUrl !== cachedImages[i]) {
            document.getElementById(`queue-${i}-albumImageUrl`).src = queue.albumImageUrl;
            cachedImages[i] = queue.albumImageUrl;
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

      if (wsinput["albumImageUrl"] !== undefined) {
        if (wsinput["albumImageUrl"] !== cachedImage) {
          document.getElementById("song-albumImageUrl").src = wsinput["albumImageUrl"];
          cachedImage = wsinput["albumImageUrl"];
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
}

const input = document.querySelector("#searchbar");

// Debounced, so holding down a key does not trigger one Spotify search per character.
input.addEventListener("input", function () {
  clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(sendSearch, SEARCH_DEBOUNCE_MS);
});

function sendSearch() {
  if (!input.value) {
    hideSearch();
    return;
  }
  send("search", input.value);
}

function markSongAdded() {
  document.getElementById("song-added").innerHTML = "Song wird hinzugefügt...";
  clearTimeout(addSongTimer);
  addSongTimer = setTimeout(() => {
    document.getElementById("song-added").innerHTML =
      "Fehler beim Hinzufügen des Songs!";
    addSongTimer = null;
  }, ADD_SONG_TIMEOUT_MS);
}

document.getElementById("queueToggle").onclick = function () {
  queueOpen = !queueOpen;
  // The server only polls the queue while somebody is actually looking at it.
  send("queue-subscribe", queueOpen ? "on" : "off");

  var content = document.getElementById("queueContent");
  var help = document.getElementById("help");
  var arrow = document.getElementById("queueArrow");
  if (queueOpen) {
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

function send(action, content) {
  if (wrongCode || !ws || ws.readyState !== WebSocket.OPEN) {
    return false;
  }
  data.action = action;
  data.content = content === undefined ? "" : content;
  ws.send(JSON.stringify(data));
  return true;
}
