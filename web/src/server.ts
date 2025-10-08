import WebSocket, { WebSocketServer } from "ws";

const PORT = 8081;
const wss = new WebSocketServer({ port: PORT });
console.log(`🚀 WebSocket relay running on ws://0.0.0.0:${PORT}`);

let androidClient: WebSocket | null = null;

wss.on("connection", (ws, req) => {
  const ip = req.socket.remoteAddress;
  console.log("✅ New connection from:", ip);

  ws.on("message", (message: WebSocket.RawData) => {
    const msg = message.toString();

    // Detect Android client (sending JPEG base64)
    if (!androidClient && msg.startsWith("/9j")) {
      androidClient = ws;
      console.log("📱 Android connected as sender");
    }

    // Broadcast frame to all other connected clients (like browsers)
    for (const client of wss.clients) {
      if (client !== ws && client.readyState === WebSocket.OPEN) {
        client.send(msg);
      }
    }
  });

  ws.on("close", () => {
    console.log("❌ Disconnected:", ip);
    if (ws === androidClient) {
      androidClient = null;
      console.log("📴 Android client removed");
    }
  });
});
