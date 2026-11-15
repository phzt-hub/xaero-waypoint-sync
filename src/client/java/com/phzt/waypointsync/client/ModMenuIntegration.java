package com.phzt.waypointsync.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu 集成：在 mods 列表里给本 mod 一个齿轮，点开就是 {@link SyncConfigScreen}。
 *
 * <p>这个类只在装了 Mod Menu 时才会被加载（Mod Menu 自己读 fabric.mod.json 里的
 * `modmenu` entrypoint），没装 Mod Menu 的客户端不会走到这里，也不会有缺类的报错。
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<SyncConfigScreen> getModConfigScreenFactory() {
        return SyncConfigScreen::new;
    }
}
