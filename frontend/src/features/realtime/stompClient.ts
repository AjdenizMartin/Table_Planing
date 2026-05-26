import type { RealtimeConnectionStatus, RealtimeEvent } from "@/features/realtime/types";

interface StompClientOptions {
  url: string;
  topics: string[];
  onEvent: (event: RealtimeEvent) => void;
  onStatusChange: (status: RealtimeConnectionStatus) => void;
}

interface StompFrame {
  command: string;
  headers: Record<string, string>;
  body: string;
}

export class RestaurantStompClient {
  private readonly options: StompClientOptions;
  private websocket: WebSocket | null = null;
  private buffer = "";
  private reconnectTimer: number | null = null;
  private subscriptionsSent = false;
  private active = false;

  constructor(options: StompClientOptions) {
    this.options = options;
  }

  connect() {
    this.active = true;
    this.openSocket();
  }

  disconnect() {
    this.active = false;
    this.subscriptionsSent = false;
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.websocket) {
      this.websocket.close();
      this.websocket = null;
    }
    this.options.onStatusChange("disconnected");
  }

  private openSocket() {
    this.options.onStatusChange("connecting");
    this.websocket = new WebSocket(this.options.url);
    this.buffer = "";
    this.subscriptionsSent = false;

    this.websocket.onopen = () => {
      this.sendFrame("CONNECT", {
        "accept-version": "1.2",
        "heart-beat": "10000,10000",
      });
    };

    this.websocket.onmessage = (event) => {
      if (typeof event.data !== "string") {
        return;
      }

      this.buffer += event.data;
      while (this.buffer.includes("\0")) {
        const separatorIndex = this.buffer.indexOf("\0");
        const rawFrame = this.buffer.slice(0, separatorIndex);
        this.buffer = this.buffer.slice(separatorIndex + 1);
        this.handleRawFrame(rawFrame);
      }
    };

    this.websocket.onerror = () => {
      this.options.onStatusChange("error");
    };

    this.websocket.onclose = () => {
      this.websocket = null;
      this.subscriptionsSent = false;
      if (!this.active) {
        this.options.onStatusChange("disconnected");
        return;
      }

      this.options.onStatusChange("disconnected");
      this.reconnectTimer = window.setTimeout(() => {
        if (this.active) {
          this.openSocket();
        }
      }, 3000);
    };
  }

  private handleRawFrame(rawFrame: string) {
    if (!rawFrame || rawFrame === "\n") {
      return;
    }

    const frame = parseFrame(rawFrame);
    if (!frame) {
      return;
    }

    if (frame.command === "CONNECTED") {
      this.options.onStatusChange("connected");
      if (!this.subscriptionsSent) {
        this.options.topics.forEach((topic, index) => {
          this.sendFrame("SUBSCRIBE", {
            id: `sub-${index}`,
            destination: topic,
          });
        });
        this.subscriptionsSent = true;
      }
      return;
    }

    if (frame.command === "MESSAGE") {
      try {
        const payload = JSON.parse(frame.body) as RealtimeEvent;
        this.options.onEvent(payload);
      } catch {
        // ignore malformed event payloads
      }
      return;
    }

    if (frame.command === "ERROR") {
      this.options.onStatusChange("error");
      this.websocket?.close();
    }
  }

  private sendFrame(command: string, headers: Record<string, string>) {
    if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
      return;
    }

    const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
    const frame = `${command}\n${headerLines.join("\n")}\n\n\0`;
    this.websocket.send(frame);
  }
}

function parseFrame(rawFrame: string): StompFrame | null {
  const normalized = rawFrame.replace(/^\n+/, "");
  if (!normalized.trim()) {
    return null;
  }

  const [headerPart, ...bodyParts] = normalized.split("\n\n");
  const headerLines = headerPart.split("\n");
  const command = headerLines.shift();
  if (!command) {
    return null;
  }

  const headers = headerLines.reduce<Record<string, string>>((accumulator, line) => {
    const separatorIndex = line.indexOf(":");
    if (separatorIndex === -1) {
      return accumulator;
    }

    const key = line.slice(0, separatorIndex);
    const value = line.slice(separatorIndex + 1);
    accumulator[key] = value;
    return accumulator;
  }, {});

  return {
    command,
    headers,
    body: bodyParts.join("\n\n").trim(),
  };
}
