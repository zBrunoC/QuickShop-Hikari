package com.ghostchu.quickshop.menu.trade;

import com.ghostchu.quickshop.QuickShop;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShopCouponManager {

    private static final File file = new File(QuickShop.getInstance().getDataFolder(), "shop_coupons.yml");
    private static final Map<Long, Map<String, Integer>> COUPONS = new ConcurrentHashMap<>();

    static {
        load();
    }

    public static void load() {
        COUPONS.clear();
        if (!file.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (final String shopIdStr : yaml.getKeys(false)) {
            try {
                final long shopId = Long.parseLong(shopIdStr);
                final Map<String, Integer> map = new ConcurrentHashMap<>();
                if (yaml.isConfigurationSection(shopIdStr)) {
                    for (final String code : yaml.getConfigurationSection(shopIdStr).getKeys(false)) {
                        map.put(code.toUpperCase(Locale.ROOT), yaml.getInt(shopIdStr + "." + code));
                    }
                }
                COUPONS.put(shopId, map);
            } catch (final NumberFormatException ignored) {}
        }
    }

    public static void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<Long, Map<String, Integer>> entry : COUPONS.entrySet()) {
            for (final Map.Entry<String, Integer> coupon : entry.getValue().entrySet()) {
                yaml.set(entry.getKey() + "." + coupon.getKey(), coupon.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (final IOException e) {
            QuickShop.getInstance().logger().error("Erro ao salvar shop_coupons.yml", e);
        }
    }

    public static void setCoupon(final long shopId, final String code, final int percent) {
        COUPONS.computeIfAbsent(shopId, k -> new ConcurrentHashMap<>()).put(code.toUpperCase(Locale.ROOT), percent);
        save();
    }

    public static boolean removeCoupon(final long shopId, final String code) {
        final Map<String, Integer> map = COUPONS.get(shopId);
        if (map != null && map.remove(code.toUpperCase(Locale.ROOT)) != null) {
            save();
            return true;
        }
        return false;
    }

    public static int getDiscount(final long shopId, final String code) {
        if (code == null) return 0;
        final Map<String, Integer> map = COUPONS.get(shopId);
        if (map != null) {
            return map.getOrDefault(code.toUpperCase(Locale.ROOT), 0);
        }
        return 0;
    }

    public static Map<String, Integer> getCoupons(final long shopId) {
        return COUPONS.getOrDefault(shopId, Collections.emptyMap());
    }
}