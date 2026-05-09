package com.decenter.server;

import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChunkLoadEvent;
import net.minestom.server.event.player.PlayerChunkUnloadEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;

/**
 * 基于 Minestom 的"中心路由分配 + 边缘计算"分布式架构 Demo。
 *
 * 核心设计：
 * - 服务端不进行任何游戏逻辑计算（红石、实体 AI 等），仅作为"全局状态数据库"和"网络路由器"。
 * - 每个区块被分配给一个玩家（边缘节点），由该玩家的客户端负责运算。
 * - 服务端负责区块授权分配、状态同步和广播。
 */
public class DecenterServer {

    private static final String CHANNEL_ID = "edge:compute";
    private static final int FLAT_Y = 40;

    public static void main(String[] args) {
        // 1. 初始化 Minestom 服务端（离线模式，无需 Mojang 正版验证）
        var server = MinecraftServer.init(new Auth.Offline());

        // 2. 创建全由石头组成的平坦世界（Instance）
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setGenerator(unit -> {
            // 从 y=0 到 y=40 填满石头，构成平坦地形
            unit.modifier().fillHeight(0, FLAT_Y, Block.STONE);
        });
        instanceContainer.setChunkSupplier(LightingChunk::new);

        // 3. 初始化功能模块
        ChunkOwnershipManager ownershipManager = new ChunkOwnershipManager(CHANNEL_ID);
        EdgeComputeChannel edgeChannel = new EdgeComputeChannel(CHANNEL_ID, instanceContainer);

        // 4. 构建分散计算事件节点（核心：区块算力授权 + 边缘通道通信）
        EventNode<Event> decenterNode = EventNode.all("decenter")
                .addListener(PlayerChunkLoadEvent.class, ownershipManager::onChunkLoad)
                .addListener(PlayerChunkUnloadEvent.class, ownershipManager::onChunkUnload)
                .addListener(PlayerPluginMessageEvent.class, edgeChannel::onPluginMessage);

        // 5. 注册全局事件
        GlobalEventHandler eventHandler = MinecraftServer.getGlobalEventHandler();
        eventHandler.addChild(decenterNode);

        // --- 玩家配置：设置出生实例和坐标 ---
        eventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instanceContainer);
            event.getPlayer().setRespawnPoint(new Pos(0, FLAT_Y + 2, 0));
        });

        // --- 玩家出生：赋予创造模式 ---
        eventHandler.addListener(PlayerSpawnEvent.class, event -> {
            event.getPlayer().setGameMode(GameMode.CREATIVE);
        });

        // 6. 启动服务端，监听所有网络接口
        server.start("0.0.0.0", 25565);
        System.out.println("============================================");
        System.out.println("[Decenter] 中心路由服务端已启动");
        System.out.println("[Decenter] 监听地址: 0.0.0.0:25565");
        System.out.println("[Decenter] 自定义通道: " + CHANNEL_ID);
        System.out.println("[Decenter] 平坦世界: 0~40 层石头");
        System.out.println("============================================");
    }
}
