"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
const ws_1 = __importStar(require("ws"));
const PORT = 8081;
const wss = new ws_1.WebSocketServer({ port: PORT });
console.log(`🚀 WebSocket relay running on ws://0.0.0.0:${PORT}`);
let androidClient = null;
wss.on("connection", (ws, req) => {
    const ip = req.socket.remoteAddress;
    console.log("✅ New connection from:", ip);
    ws.on("message", (message) => {
        const msg = message.toString();
        // Detect Android client (sending JPEG base64)
        if (!androidClient && msg.startsWith("/9j")) {
            androidClient = ws;
            console.log("📱 Android connected as sender");
        }
        // Broadcast frame to all other connected clients (like browsers)
        for (const client of wss.clients) {
            if (client !== ws && client.readyState === ws_1.default.OPEN) {
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
