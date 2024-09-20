window.onload = setupWebSocket;
let ws;
let uri;
let uri1;
let uri2;
let uri3;
let sentValue;

let songAdded;


function hideSearch() {
    document.getElementById("search-1-button").style.visibility = "hidden";
    document.getElementById("search-1-name").style.visibility = "hidden";
    document.getElementById("search-1-artists").style.visibility = "hidden";
    document.getElementById("search-1-cover").style.visibility = "hidden";

    document.getElementById("search-2-button").style.visibility = "hidden";
    document.getElementById("search-2-name").style.visibility = "hidden";
    document.getElementById("search-2-artists").style.visibility = "hidden";
    document.getElementById("search-2-cover").style.visibility = "hidden";

    document.getElementById("search-3-button").style.visibility = "hidden";
    document.getElementById("search-3-name").style.visibility = "hidden";
    document.getElementById("search-3-artists").style.visibility = "hidden";
    document.getElementById("search-3-cover").style.visibility = "hidden";
}

function setupWebSocket() {
    ws = new WebSocket("wss://" + location.hostname + ":" + location.port + "/main");

    setInterval(refresh, 2000);

    hideSearch();
    ws.onmessage = messageEvent => {
        if (messageEvent.data.includes("QUEUE-LENGTH: ")) {
            let queueLength = messageEvent.data.replace("QUEUE-LENGTH: ", "");
            if (queueLength == -1) {
                document.getElementById("song-added").innerHTML = "Fehler beim Hinzufügen des Songs!";
            } else {
                document.getElementById("song-added").innerHTML = "Lied spielt in ca. " + queueLength + " min";
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
        if (document.querySelector('#searchbar').value != sentValue) {
            if (wsinput.hasOwnProperty("search-1")) {
                let search1 = wsinput["search-1"];
                document.getElementById("search-1-name").style.visibility = "visible";
                document.getElementById("search-1-name").innerHTML = search1["name"];
                document.getElementById("song-added").innerHTML = " ";
                document.getElementById("search-1-artists").style.visibility = "visible";
                document.getElementById("search-1-artists").innerHTML = search1["artists"];
                document.getElementById("search-1-cover").style.visibility = "visible";
                document.getElementById("search-1-cover").src = search1["cover"];
                document.getElementById("search-1-button").style.visibility = "visible";
                uri1 = search1["uri"];
                var button1 = document.getElementById("search-1-button");
                if (search1["played"] == true) {
                    button1.style.backgroundColor = "#FFA500";
                    button1.style.borderColor = "#FFA500";
                } else {
                    button1.style.backgroundColor = "";
                    button1.style.borderColor = "";
                }
            }
            if (wsinput.hasOwnProperty("search-2")) {
                let search2 = wsinput["search-2"];
                document.getElementById("search-2-name").style.visibility = "visible";
                document.getElementById("search-2-name").innerHTML = search2["name"];
                document.getElementById("search-2-artists").style.visibility = "visible";
                document.getElementById("search-2-artists").innerHTML = search2["artists"];
                document.getElementById("search-2-cover").style.visibility = "visible";
                document.getElementById("search-2-cover").src = search2["cover"];
                document.getElementById("search-2-button").style.visibility = "visible";
                uri2 = search2["uri"];
                var button2 = document.getElementById("search-2-button");
                if (search2["played"] == true) {
                    button2.style.backgroundColor = "#FFA500";
                    button2.style.borderColor = "#FFA500";
                } else {
                    button2.style.backgroundColor = "";
                    button2.style.borderColor = "";
                }
            }
            if (wsinput.hasOwnProperty("search-3")) {
                let search3 = wsinput["search-3"];
                document.getElementById("search-3-name").style.visibility = "visible";
                document.getElementById("search-3-name").innerHTML = search3["name"];
                document.getElementById("search-3-artists").style.visibility = "visible";
                document.getElementById("search-3-artists").innerHTML = search3["artists"];
                document.getElementById("search-3-cover").style.visibility = "visible";
                document.getElementById("search-3-cover").src = search3["cover"];
                document.getElementById("search-3-button").style.visibility = "visible";
                uri3 = search3["uri"];
                var button3 = document.getElementById("search-3-button");
                if (search3["played"] == true) {
                    button3.style.backgroundColor = "#FFA500";
                    button3.style.borderColor = "#FFA500";
                } else {
                    button3.style.backgroundColor = "";
                    button3.style.borderColor = "";
                }
            }
        }
    }
    ws.onclose = closeEvent => {
        if (ws.readyState == 0) {
            return;
        } else {
            ws = null;
            setupWebSocket();
        }
    }
}
// Buttons for selecting the right song
document.getElementById('search-1-button').onclick = function () {
    ws.send("Song-Play: " + uri1);
    hideSearch();
    document.getElementById("song-added").innerHTML = "Song wird hinzugefügt...";
    sentValue = document.querySelector('#searchbar').value;
    songAdded = true;
}
document.getElementById('search-2-button').onclick = function () {
    ws.send("Song-Play: " + uri2);
    hideSearch();
    document.getElementById("song-added").innerHTML = "Song wird hinzugefügt...";
    sentValue = document.querySelector('#searchbar').value;
    songAdded = true;
}
document.getElementById('search-3-button').onclick = function () {
    ws.send("Song-Play: " + uri3);
    hideSearch();
    document.getElementById("song-added").innerHTML = "Song wird hinzugefügt...";
    sentValue = document.querySelector('#searchbar').value;
    songAdded = true;
}
document.getElementById('song-cover').onclick = function () {
    location.href = url;
}

// refresh function which gets timed every 1000 ms (on the top)
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
            document.getElementById("song-added").innerHTML = "Fehler beim Hinzufügen des Songs!";
            counter = 0;
            songAdded = false;
            return;
        } else {
            counter++;
        }
    }
}

const input = document.querySelector('#searchbar');

input.addEventListener('change', updateValue);

function updateValue() {
    if (input.value !== null && input.value !== "") {
        ws.send("Search: " + input.value);
    }
}

