const img = document.getElementById("img") as HTMLImageElement;
const fpsText = document.getElementById("fps") as HTMLElement;

// Connect to relay (same PC running the Node relay)
const WS_URL = "ws://localhost:8081";
const socket = new WebSocket(WS_URL);

socket.onopen = () => {
  console.log("✅ Connected to relay");
  socket.send("WEB_HELLO"); // identify browser client
};

let fps = 0;
let lastTime = Date.now();

socket.onmessage = (event) => {
  const data = event.data;
  if (typeof data === "string" && data.startsWith("data:image")) {
    img.src = data;
    fps++;

    const now = Date.now();
    if (now - lastTime >= 1000) {
      fpsText.textContent = `FPS: ${fps}`;
      fps = 0;
      lastTime = now;
    }
  }
};
