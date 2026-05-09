package com.decenter.server;

import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerChunkLoadEvent;
import net.minestom.server.event.player.PlayerChunkUnloadEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 区块算力授权管理器。
 *
 * 维护 Map&lt;ChunkKey, Player&gt; 注册表，记录每个区块当前被分配给了哪个玩家（Owner）。
 * 当玩家 A 走入无主区块时，自动获得该区块的算力所有权；
 * 当玩家 A 离开自己拥有的区块时，所有权被撤销。
 *
 * 线程安全：使用 ConcurrentHashMap 保证多玩家并发操作安全。
 */
public class ChunkOwnershipManager {

    private final String channelId;

    // 将 (chunkX, chunkZ) 打包为一个 long 作为 key
    // 高 32 位存 chunkX，低 32 位存 chunkZ
    private final ConcurrentMap<Long, Player> chunkOwners;

    public ChunkOwnershipManager(String channelId) {
        this.channelId = channelId;
        this.chunkOwners = new ConcurrentHashMap<>();
    }

    // ======================== 核心事件处理 ========================

    /**
     * 当玩家加载一个区块时（即玩家走进该区块），检查并分配算力所有权。
     * putIfAbsent: 原子操作 —— 只有该区块尚无 Owner 时才分配。
     */
    public void onChunkLoad(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        int chunkX = event.getChunkX();
        int chunkZ = event.getChunkZ();
        long key = pack(chunkX, chunkZ);

        Player previousOwner = chunkOwners.putIfAbsent(key, player);
        if (previousOwner == null) {
            // 之前无主，当前玩家获得算力所有权
            String json = ChunkGrantMessage.toJson("grant", chunkX, chunkZ);
            player.sendPluginMessage(channelId, json);
            System.out.printf("[Decenter] GRANT  | 玩家 %s 获得区块 (%d, %d) 的算力授权%n",
                    player.getUsername(), chunkX, chunkZ);
        }
    }

    /**
     * 当玩家卸载一个区块时（离开或超出视野），如果该玩家正是 Owner，则撤销所有权。
     * remove(key, value): 原子操作 —— 只有该玩家确实是 Owner 时才移除。
     */
    public void onChunkUnload(PlayerChunkUnloadEvent event) {
        Player player = event.getPlayer();
        int chunkX = event.getChunkX();
        int chunkZ = event.getChunkZ();
        long key = pack(chunkX, chunkZ);

        boolean removed = chunkOwners.remove(key, player);
        if (removed) {
            // 确认是该区块 Owner，撤销算力所有权
            String json = ChunkGrantMessage.toJson("revoke", chunkX, chunkZ);
            player.sendPluginMessage(channelId, json);
            System.out.printf("[Decenter] REVOKE | 玩家 %s 失去区块 (%d, %d) 的算力授权%n",
                    player.getUsername(), chunkX, chunkZ);
        }
    }

    // ======================== 公开查询方法 ========================

    /**
     * 查询指定区块的 Owner。
     * @return 如果该区块有 Owner 则返回对应 Player，否则返回 null
     */
    public Player getOwner(int chunkX, int chunkZ) {
        return chunkOwners.get(pack(chunkX, chunkZ));
    }

    /**
     * 查询指定方块坐标所属区块的 Owner。
     */
    public Player getOwnerAt(int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        return getOwner(chunkX, chunkZ);
    }

    /**
     * 返回当前注册的区块所有权映射的不可变快照。
     */
    public ConcurrentMap<Long, Player> getOwnershipMap() {
        return chunkOwners;
    }

    // ======================== 辅助方法 ========================

    /**
     * 将两个 int 打包为一个 long。高 32 位存 chunkX，低 32 位存 chunkZ。
     */
    static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFF_FFFFL);
    }
}
