window.onload = init;

let ws;
let url;
let uri1;
let uri2;
let uri3;
let sentValue;
let cachedImage;
let sessionCode;

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

let port = "";

if (location.port != "") {
  port = ":" + location.port;
}

const mainLink = location.protocol + "//" + location.hostname + port;

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
  if (reconnectTimer) {
    return;
  }
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    setupWebSocket();
  }, reconnectDelay);
  reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
}

function handleMessage(messageEvent) {
  if (messageEvent.data == "close") {
    window.location.href = mainLink + "/login.html";
    return;
  }
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
  try {
    let wsinput = JSON.parse(messageEvent.data);

    if (wsinput["user"] !== undefined) {
      document.getElementById("username").innerHTML = wsinput["user"];
    }
    if (wsinput["sessionCode"] !== undefined) {
      sessionCode = wsinput["sessionCode"];
      document.getElementById("sessionCode").innerHTML = sessionCode;

      setupShareButton();
    } else if (wsinput["auth-url"] !== undefined) {
      window.location.href = wsinput["auth-url"];
    }

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

function markSongAdded() {
  document.getElementById("song-added").innerHTML = "Song wird hinzugefügt...";
  clearTimeout(addSongTimer);
  addSongTimer = setTimeout(() => {
    document.getElementById("song-added").innerHTML =
      "Fehler beim Hinzufügen des Songs!";
    addSongTimer = null;
  }, ADD_SONG_TIMEOUT_MS);
}

// If input is entered, the value gets sent to the websocket
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

let data = {
  adminCode: location.search.replace("?", ""),
  action: "",
  content: "",
};

function send(action, content) {
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    return false;
  }
  data.action = action;
  data.content = content === undefined ? "" : content;
  ws.send(JSON.stringify(data));
  return true;
}

document.getElementById("back-button").onclick = function () {
  send("BACK");
};
document.getElementById("play-button").onclick = function () {
  send("PLAYPAUSE");
};
document.getElementById("vorward-button").onclick = function () {
  send("NEXT");
};
document.getElementById("toggle-state").onclick = function () {
  send("TOGGLE-STATE");
};

document.getElementById("change-user-button").onclick = function () {
  send("CHANGEUSER");
};

document.getElementById("generate-session-code").onclick = function () {
  if (
    confirm(
      "Möchtest du den Session-Code neu generieren? Dadurch werden alle eingeloggten User ausgeloggt!"
    )
  ) {
    send("NEW-SESSION");
  }
};

function setupShareButton() {
  const btn = document.getElementById("share-session-button");
  btn.removeEventListener("click", shareHandler);
  btn.addEventListener("click", shareHandler);
}

function shareHandler() {
  const shareData = {
    title: "BytePhil Music",
    text:
      "Füge Songs zur Warteschlange hinzu! \nZugangscode: " +
      sessionCode +
      " oder nutze den Link: \n ",
    url: mainLink + "?" + sessionCode + "/",
  };

  {
    if (navigator.share) {
      navigator.share(shareData).catch((err) => {
        console.error("Share failed:", err.message);
      });
    } else {
      const fallbackUrl = `https://api.whatsapp.com/send?text=${encodeURIComponent(
        shareData.title + "\n" + shareData.text + "\n" + shareData.url
      )}`;
      window.open(fallbackUrl, "_blank");
    }
  }
}
