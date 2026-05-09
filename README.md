# Decenter Server

A Minestom-based server implementing **centralized routing + edge computing** architecture for Minecraft. The server does not run any game logic (redstone, entity AI, etc.) and instead acts purely as a global state database and network router.

## Core Concept

```
┌─────────────────────────────────────┐
│           Decenter Server           │
│    (Global state + Network hub)     │
├─────────────────────────────────────┤
│  Chunk Owner Registry               │
│  ┌───────┬───────┬───────┐          │
│  │ (0,0) │ (1,0) │ (2,0) │  ...     │
│  │ Alice │ Bob   │ Alice │          │
│  └───────┴───────┴───────┘          │
├─────────────────────────────────────┤
│  Plugin Channel: edge:compute       │
│    ↓ grant/revoke    ↑ block_updates│
└──────┬──────────────────────────────┘
       │
   ┌───┴────┬────────┐
   ▼        ▼        ▼
┌─────┐ ┌─────┐ ┌─────┐
│Alice│ │ Bob │ │ ... │
│(Edge)│(Edge)│(Edge)│
└─────┘ └─────┘ └─────┘
```

Each chunk is assigned to exactly **one player** (the "owner"). That player's client is responsible for computing what happens in that chunk. Results are sent back to the server via the `edge:compute` plugin channel, and the server broadcasts the state change to all nearby players.

## Protocol

### Server → Client (`edge:compute`)

```json
{"action": "grant",  "chunkX": 5, "chunkZ": -3}
{"action": "revoke", "chunkX": 5, "chunkZ": -3}
```

- `grant` — Sent when the player enters a chunk that has no owner. The player is now the owner of that chunk.
- `revoke` — Sent when the player leaves a chunk they own. Ownership is released.

### Client → Server (`edge:compute`)

```json
{"action": "update_block", "x": 10, "y": 40, "z": 15, "block": "minecraft:gold_block"}
```

- `update_block` — Set a block at the given coordinates. The server applies the change to the world, which automatically broadcasts to all players who can see that position.

## Quick Start

### Prerequisites

- Java 25+
- Gradle 9.5+ (or use the wrapper)

### Build & Run

```bash
git clone https://github.com/Huoyucat/decenter-server.git
cd decenter-server
gradle run --no-daemon
```

The server starts on `0.0.0.0:25565` in offline mode. Connect with any Minecraft 1.21.11 client.

### World

A flat stone world (y=0 to 40). Players spawn at (0, 42, 0) in creative mode.

## Client Mod Integration

To build an edge-compute client mod:

1. **Register the `edge:compute` channel** with Fabric/NeoForge networking API.
2. **Handle `grant`** — Start your compute loop for the chunk. Example: place blinking lights.
3. **Handle `revoke`** — Stop the compute loop.
4. **Send `update_block`** — Push computed block changes back to the server.

Minimal Fabric example:

```java
// Receiving grant/revoke
ClientPlayNetworking.registerGlobalReceiver(EDGE_COMPUTE, (payload, context) -> {
    String json = payload.getString();
    if (json.contains("grant")) {
        // parse chunkX, chunkZ — start computing
    } else if (json.contains("revoke")) {
        // stop computing
    }
});

// Sending block updates
String msg = "{\"action\":\"update_block\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + ",\"block\":\"minecraft:glowstone\"}";
ClientPlayNetworking.send(new CustomPayload(EDGE_COMPUTE, msg.getBytes(StandardCharsets.UTF_8)));
```

## Why This Architecture?

- **Server-light** — The server never computes redstone, mob AI, or world generation. It only routes state changes.
- **Scalable** — Each player's machine handles their assigned chunks. Adding players adds compute capacity.
- **Authoritative** — The server remains the single source of truth for world state. Clients cannot cheat blocks into existence — they can only compute within their assigned chunks.
- **Bandwidth-efficient** — Only state diffs travel over the network, not full chunk data.

## License

MIT
