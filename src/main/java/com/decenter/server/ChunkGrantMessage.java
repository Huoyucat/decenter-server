package com.decenter.server;

/**
 * 边缘计算协议 JSON 消息构造器。
 *
 * 下行（Server → Client）协议格式：
 *   grant:  {"action": "grant",  "chunkX": <int>, "chunkZ": <int>}
 *   revoke: {"action": "revoke", "chunkX": <int>, "chunkZ": <int>}
 */
final class ChunkGrantMessage {

    private ChunkGrantMessage() {}

    /**
     * 构建 grant/revoke 消息的 JSON 字符串。
     */
    static String toJson(String action, int chunkX, int chunkZ) {
        return "{\"action\":\"" + action + "\",\"chunkX\":" + chunkX + ",\"chunkZ\":" + chunkZ + "}";
    }
}
