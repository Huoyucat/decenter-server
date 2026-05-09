package com.decenter.server;

import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 边缘计算自定义通道处理器。
 *
 * 负责监听来自客户端 edge:compute 通道的上行数据包。
 * 上行协议格式（Client → Server）：
 *   {"action": "update_block", "x": <int>, "y": <int>, "z": <int>, "block": "namespace:block_id"}
 *
 * 收到后直接将方块更新应用到 Instance，由 Minestom 自动广播给所有能看到该区域的玩家。
 */
final class EdgeComputeChannel {

    private final String channelId;
    private final Instance instance;

    // 解析上行 JSON 中的 update_block 消息（简单的正则提取，避免引入完整 JSON 库）
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "\"action\"\\s*:\\s*\"update_block\"" +
            ".*?\"x\"\\s*:\\s*(-?\\d+)" +
            ".*?\"y\"\\s*:\\s*(-?\\d+)" +
            ".*?\"z\"\\s*:\\s*(-?\\d+)" +
            ".*?\"block\"\\s*:\\s*\"([^\"]+)\""
    );

    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "\"action\"\\s*:\\s*\"([^\"]+)\""
    );

    EdgeComputeChannel(String channelId, Instance instance) {
        this.channelId = channelId;
        this.instance = instance;
    }

    /**
     * 处理来自客户端的 PluginMessage 事件。
     * 只响应 edge:compute 通道的消息。
     */
    void onPluginMessage(PlayerPluginMessageEvent event) {
        if (!channelId.equals(event.getIdentifier())) {
            return; // 非本通道消息，忽略
        }

        String message = event.getMessageString();
        if (message == null || message.isEmpty()) {
            return;
        }

        // 从消息中提取 action 字段，根据 action 类型分发处理
        Matcher actionMatcher = ACTION_PATTERN.matcher(message);
        if (!actionMatcher.find()) {
            return;
        }

        String action = actionMatcher.group(1);

        switch (action) {
            case "update_block" -> handleBlockUpdate(message, event.getPlayer().getUsername());
            default -> System.out.printf("[Decenter] 收到未知 action: %s (来自 %s)%n",
                    action, event.getPlayer().getUsername());
        }
    }

    /**
     * 处理区块方块更新请求。
     * 从远程边缘节点收到计算后的方块状态，直接写入 Instance。
     * Minestom 的 setBlock() 会自动将变更广播给所有在视野内的玩家。
     */
    private void handleBlockUpdate(String message, String sourcePlayer) {
        Matcher matcher = UPDATE_PATTERN.matcher(message);
        if (!matcher.find()) {
            System.out.printf("[Decenter] 收到格式错误的 update_block 消息: %s%n", message);
            return;
        }

        try {
            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int z = Integer.parseInt(matcher.group(3));
            String blockId = matcher.group(4);

            // 从 Minecraft 命名空间 ID 解析 Block 类型
            // 例如 "minecraft:gold_block" -> Block.GOLD_BLOCK
            Block block = Block.fromKey(blockId);

            // 将方块写入 Instance，这会自动广播给所有相关玩家
            instance.setBlock(x, y, z, block);

            System.out.printf("[Decenter] BLOCK_UPDATE | 玩家 %s → 在 (%d, %d, %d) 放置 %s%n",
                    sourcePlayer, x, y, z, blockId);
        } catch (NumberFormatException e) {
            System.out.printf("[Decenter] 解析坐标失败: %s%n", message);
        }
    }
}
