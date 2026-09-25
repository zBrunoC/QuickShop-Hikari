package com.ghostchu.quickshop.menu.keeper;
/*
 * QuickShop-Hikari
 * Copyright (C) 2024 Daniel "creatorfromhell" Vidmar
 */

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.database.bean.DataRecord;
import com.ghostchu.quickshop.api.event.display.ItemPreviewComponentPrePopulateEvent;
import com.ghostchu.quickshop.api.inventory.InventoryWrapper;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.permission.BuiltInShopPermission;
import com.ghostchu.quickshop.config.GuiConfig;
import com.ghostchu.quickshop.menu.shared.GuiChatAction;
import com.ghostchu.quickshop.menu.shared.QuickShopPage;
import com.ghostchu.quickshop.menu.trade.ShopCouponManager;
import com.ghostchu.quickshop.obj.QUserImpl;
import com.ghostchu.quickshop.shop.history.ShopHistory;
import com.ghostchu.quickshop.util.ShopUtil;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.logger.Log;
import com.ghostchu.quickshop.util.logging.container.ShopRemoveLog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.tnemc.item.AbstractItemStack;
import net.tnemc.item.providers.SkullProfile;
import net.tnemc.menu.core.builder.IconBuilder;
import net.tnemc.menu.core.callbacks.page.PageOpenCallback;
import net.tnemc.menu.core.compatibility.MenuPlayer;
import net.tnemc.menu.core.icon.action.impl.RunnableAction;
import net.tnemc.menu.core.icon.action.impl.SwitchMenuAction;
import net.tnemc.menu.core.icon.impl.StateIcon;
import net.tnemc.menu.core.manager.MenuManager;
import net.tnemc.menu.core.viewer.MenuViewer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.ghostchu.quickshop.menu.ShopHistoryMenu.HISTORY_DATA_RECORDS;
import static com.ghostchu.quickshop.menu.ShopHistoryMenu.HISTORY_RECORDS;
import static com.ghostchu.quickshop.menu.ShopHistoryMenu.HISTORY_SUMMARY;
import static com.ghostchu.quickshop.menu.ShopHistoryMenu.SHOPS_DATA;
import static com.ghostchu.quickshop.menu.ShopHistoryMenu.SHOPS_HEADERS;
import static com.ghostchu.quickshop.menu.ShopKeeperMenu.KEEPER_MAIN;
import static com.ghostchu.quickshop.shop.SimpleShopManager.ACTIVE_STATE;
import static com.ghostchu.quickshop.shop.SimpleShopManager.BUYING_TYPE;
import static com.ghostchu.quickshop.shop.SimpleShopManager.FROZEN_STATE;
import static com.ghostchu.quickshop.shop.SimpleShopManager.SELLING_TYPE;

public class MainPage extends QuickShopPage {

  private static Component mm(String text) {
    return MiniMessage.miniMessage().deserialize(text);
  }

  public MainPage() {
    super(KEEPER_MAIN);
    setOpen(this::open);
  }

  public void open(final PageOpenCallback open) {
    final UUID id = open.getPlayer().identifier();
    final Optional<MenuViewer> viewer = open.getPlayer().viewer();

    if(viewer.isPresent()) {
      final Optional<Shop> shop = getShop(viewer.get());
      final Player player = Bukkit.getPlayer(id);

      if(shop.isPresent() && player != null) {
        final QuickShop plugin = QuickShop.getInstance();
        open.getPage().getIcons().clear();

        final GuiConfig.MenuConfig menuConfig = plugin.getGuiConfig().getMenuConfig("keeper");
        final GuiConfig.IconConfig borderConfig = menuConfig != null? menuConfig.getIcon("border") : null;
        final GuiConfig.IconConfig shopItemConfig = menuConfig != null? menuConfig.getIcon("shop-item") : null;
        final GuiConfig.IconConfig changePriceConfig = menuConfig != null? menuConfig.getIcon("change-price") : null;
        final GuiConfig.IconConfig displayToggleConfig = menuConfig != null? menuConfig.getIcon("display-toggle") : null;
        final GuiConfig.IconConfig freezeToggleConfig = menuConfig != null? menuConfig.getIcon("freeze-toggle") : null;
        final GuiConfig.IconConfig modeToggleConfig = menuConfig != null? menuConfig.getIcon("mode-toggle") : null;
        final GuiConfig.IconConfig inventoryConfig = menuConfig != null? menuConfig.getIcon("inventory") : null;
        final GuiConfig.IconConfig staffConfig = menuConfig != null? menuConfig.getIcon("staff") : null;
        final GuiConfig.IconConfig historyConfig = menuConfig != null? menuConfig.getIcon("history") : null;
        final GuiConfig.IconConfig removeConfig = menuConfig != null? menuConfig.getIcon("remove") : null;
        final GuiConfig.IconConfig closeConfig = menuConfig != null? menuConfig.getIcon("close") : null;

        final String borderMaterial = borderConfig != null? borderConfig.getMaterial() : "GRAY_STAINED_GLASS_PANE";
        final IconBuilder borderBuilder = new IconBuilder(plugin.stack().of(borderMaterial, 1));
        final List<Integer> borderRows = borderConfig != null? borderConfig.getRows() : List.of(2, 4);
        for(final int row : borderRows) {
          open.getPage().setRow(row, borderBuilder);
        }

        final ItemStack shopItem = shop.get().getItem();
        final int shopItemSlot = shopItemConfig != null? shopItemConfig.getSlot() : 4;
        final ItemPreviewComponentPrePopulateEvent previewComponentPrePopulateEvent = new ItemPreviewComponentPrePopulateEvent(shopItem, player);
        previewComponentPrePopulateEvent.callEvent();
        final AbstractItemStack<ItemStack> shopItemStack = plugin.stack(previewComponentPrePopulateEvent.getItemStack());
        open.getPage().addIcon(new IconBuilder(shopItemStack).withSlot(shopItemSlot).build());

        final double currentPrice = shop.get().getPrice();

        // 1. Alternar Item Flutuante (Slot 18)
        if(shop.get().playerAuthorize(id, BuiltInShopPermission.TOGGLE_DISPLAY)
                || plugin.perm().hasPermission(player, "quickshop.other.toggledisplay")) {
          final AbstractItemStack<?> activeStack = plugin.stack().of("GLOW_ITEM_FRAME", 1)
                  .customName(mm("<!italic><gradient:#00F2FE:#4FACFE><bold>ɪᴛᴇᴍ ғʟᴜᴛᴜᴀɴᴛᴇ</bold></gradient>"))
                  .lore(List.of(mm("<!italic><gray>ᴀʟᴛᴇʀɴᴀ ᴀ ᴇxɪʙɪçãᴏ ᴅᴏ ɪᴛᴇᴍ ɴᴏ ʙᴀú."), Component.empty(), mm("<!italic><#00F2FE>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀᴛɪᴠᴀʀ ᴏᴜ ᴅᴇsᴀᴛɪᴠᴀʀ.</#00F2FE>")));
          final AbstractItemStack<?> inactiveStack = plugin.stack().of("ITEM_FRAME", 1)
                  .customName(mm("<!italic><gradient:#00F2FE:#4FACFE><bold>ɪᴛᴇᴍ ғʟᴜᴛᴜᴀɴᴛᴇ</bold></gradient>"))
                  .lore(List.of(mm("<!italic><gray>ᴀʟᴛᴇʀɴᴀ ᴀ ᴇxɪʙɪçãᴏ ᴅᴏ ɪᴛᴇᴍ ɴᴏ ʙᴀú."), Component.empty(), mm("<!italic><#00F2FE>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀᴛɪᴠᴀʀ ᴏᴜ ᴅᴇsᴀᴛɪᴠᴀʀ.</#00F2FE>")));

          final String modeState = (!shop.get().isDisableDisplay())? "ACTIVE" : "INACTIVE";
          final StateIcon changeIcon = new StateIcon(activeStack, null, "SHOP_DISPLAY", modeState, (currentState)->{
            if(currentState.toUpperCase(Locale.ROOT).equals("ACTIVE")) {
              Util.regionThread(shop.get().bukkitLocation(), ()->shop.get().setDisableDisplay(true));
              return "INACTIVE";
            } else {
              Util.regionThread(shop.get().bukkitLocation(), ()->shop.get().setDisableDisplay(false));
              return "ACTIVE";
            }
          });
          changeIcon.setSlot(18);
          changeIcon.addState("ACTIVE", activeStack);
          changeIcon.addState("INACTIVE", inactiveStack);
          open.getPage().addIcon(changeIcon);
        }

        // 2. Alterar Preço (Slot 19)
        if(shop.get().playerAuthorize(id, BuiltInShopPermission.SET_PRICE) || plugin.perm().hasPermission(player, "quickshop.other.price")) {
          open.getPage().addIcon(new IconBuilder(plugin.stack().of("GOLD_NUGGET", 1)
                  .customName(mm("<!italic><gradient:#FFE259:#FFA751><bold>ᴀʟᴛᴇʀᴀʀ ᴘʀᴇçᴏ</bold></gradient>"))
                  .lore(List.of(mm("<!italic><gray>ᴘʀᴇçᴏ ᴀᴛᴜᴀʟ: <#FFE259>" + currentPrice + " Coins</#FFE259>"), Component.empty(), mm("<!italic><#FFA751>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴅɪɢɪᴛᴀʀ ᴜᴍ ɴᴏᴠᴏ ᴘʀᴇçᴏ.</#FFA751>"))))
                  .withActions(new GuiChatAction((message)->{
                    if(!message.isEmpty()) {
                      try {
                        final BigDecimal price = new BigDecimal(message);
                        Util.regionThread(shop.get().bukkitLocation(), ()->{
                          ShopUtil.setPrice(plugin, QUserImpl.createFullFilled(player), price.doubleValue(), shop.get());
                          final MenuPlayer menuPlayer = plugin.createMenuPlayer(player);
                          menuPlayer.inventory().openMenu(menuPlayer, "qs:keeper", KEEPER_MAIN);
                        });
                        return true;
                      } catch(final NumberFormatException ignore) { }
                    }
                    return true;
                  }, "§6§l[Loja] §eDigite o novo preço no chat:", false))
                  .withSlot(19).build());
        }

        // 3. Congelar Loja (Slot 20)
        if(shop.get().playerAuthorize(id, BuiltInShopPermission.SET_SHOP_STATE) || plugin.perm().hasPermission(player, "quickshop.togglefreeze")) {
          final AbstractItemStack<?> freezeStack = plugin.stack().of("LIGHT_BLUE_CONCRETE", 1)
                  .customName(mm("<!italic><gradient:#36D1DC:#5B86E5><bold>ᴄᴏɴɢᴇʟᴀʀ ʟᴏᴊᴀ</bold></gradient>"))
                  .lore(List.of(mm("<!italic><gray>ᴘᴀᴜsᴀ ᴛᴏᴅᴀs ᴀs ᴛʀᴀɴsᴀçõᴇs ᴅᴀ ʟᴏᴊᴀ."), Component.empty(), mm("<!italic><#5B86E5>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀʟᴛᴇʀɴᴀʀ ᴏ ᴇsᴛᴀᴅᴏ.</#5B86E5>")));
          final AbstractItemStack<?> unfreezeStack = plugin.stack().of("RED_CONCRETE", 1)
                  .customName(mm("<!italic><gradient:#36D1DC:#5B86E5><bold>ᴄᴏɴɢᴇʟᴀʀ ʟᴏᴊᴀ</bold></gradient>"))
                  .lore(List.of(mm("<!italic><gray>ᴘᴀᴜsᴀ ᴛᴏᴅᴀs ᴀs ᴛʀᴀɴsᴀçõᴇs ᴅᴀ ʟᴏᴊᴀ."), Component.empty(), mm("<!italic><#5B86E5>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀʟᴛᴇʀɴᴀʀ ᴏ ᴇsᴛᴀᴅᴏ.</#5B86E5>")));

          final String modeState = shop.get().shopState().identifier().toUpperCase(Locale.ROOT);
          final StateIcon changeIcon = new StateIcon(freezeStack, null, "SHOP_STATE", modeState, (currentState)->{
            if(currentState.toUpperCase(Locale.ROOT).equals("ACTIVE")) {
              Util.regionThread(shop.get().bukkitLocation(), ()->shop.get().shopState(FROZEN_STATE));
              return "FROZEN";
            } else {
              Util.regionThread(shop.get().bukkitLocation(), ()->shop.get().shopState(ACTIVE_STATE));
              return "ACTIVE";
            }
          });
          changeIcon.setSlot(20);
          changeIcon.addState("FROZEN", freezeStack);
          changeIcon.addState("ACTIVE", unfreezeStack);
          open.getPage().addIcon(changeIcon);
        }

        // 4. Modo da Loja (Slot 21)
        if(shop.get().playerAuthorize(id, BuiltInShopPermission.SET_SHOPTYPE) || plugin.perm().hasPermission(player, "quickshop.create.buy")) {
          final AbstractItemStack<?> buyingStack = plugin.stack().of("LIME_CONCRETE", 1)
                  .customName(mm("<!italic><gradient:#56AB2F:#A8E063><bold>ᴍᴏᴅᴏ ᴅᴀ ʟᴏᴊᴀ</bold></gradient>"))
                  .lore(List.of(mm("<!italic><gray>ᴍᴏᴅᴏ ᴀᴛᴜᴀʟ: <green>ᴠᴇɴᴅᴇɴᴅᴏ</green>"), Component.empty(), mm("<!italic><#A8E063>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴍᴜᴅᴀʀ (ᴄᴏᴍᴘʀᴀ/ᴠᴇɴᴅᴀ).</#A8E063>")));
          final AbstractItemStack<?> sellingStack = plugin.stack().of("ORANGE_CONCRETE", 1)
                  .customName(mm("<!italic><gradient:#56AB2F:#A8E063><bold>ᴍᴏᴅᴏ ᴅᴀ ʟᴏᴊᴀ</bold></gradient>"))
                  .lore(List.of(mm("<!italic><gray>ᴍᴏᴅᴏ ᴀᴛᴜᴀʟ: <gold>ᴄᴏᴍᴘʀᴀɴᴅᴏ</gold>"), Component.empty(), mm("<!italic><#A8E063>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴍᴜᴅᴀʀ (ᴄᴏᴍᴘʀᴀ/ᴠᴇɴᴅᴀ).</#A8E063>")));

          final String modeState = shop.get().shopType().identifier().toUpperCase(Locale.ROOT);
          final StateIcon changeIcon = new StateIcon(buyingStack, null, "SHOP_TYPE", modeState, (currentState)->{
            if(currentState.toUpperCase(Locale.ROOT).equals("SELLING")) {
              if(!ShopUtil.canChangeShopType(plugin, player, shop.get(), BUYING_TYPE)) return currentState;
              Util.regionThread(shop.get().bukkitLocation(), ()->shop.get().shopType(BUYING_TYPE));
              return "BUYING";
            } else {
              if(!ShopUtil.canChangeShopType(plugin, player, shop.get(), SELLING_TYPE)) return currentState;
              Util.regionThread(shop.get().bukkitLocation(), ()->shop.get().shopType(SELLING_TYPE));
              return "SELLING";
            }
          });
          changeIcon.setSlot(21);
          changeIcon.addState("SELLING", buyingStack);
          changeIcon.addState("BUYING", sellingStack);
          open.getPage().addIcon(changeIcon);
        }

        // 5. Estoque (Slot 22)
        final InventoryWrapper inventory = shop.get().getInventory();
        if(inventory != null && inventory.getHolder() != null) {
          open.getPage().addIcon(new IconBuilder(plugin.stack().of("CHEST", 1)
                  .customName(mm("<!italic><gradient:#00C9FF:#92FE9D><bold>ᴇsᴛᴏǫᴜᴇ ᴅᴀ ʟᴏᴊᴀ</bold></gradient>"))
                  .lore(List.of(mm("<!italic><gray>ᴀʙʀᴇ ᴏ ɪɴᴠᴇɴᴛáʀɪᴏ ᴅᴏ ʙᴀú."), Component.empty(), mm("<!italic><#92FE9D>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀᴄᴇssᴀʀ ᴏs ɪᴛᴇɴs.</#92FE9D>"))))
                  .withSlot(22)
                  .withActions(new RunnableAction((click)->{
                    viewer.get().close(plugin.createMenuPlayer(player));
                    Util.regionThread(shop.get().bukkitLocation(), ()->{
                      player.openInventory(inventory.getHolder().getInventory());
                      QuickShop.inShop.add(player.getUniqueId());
                    });
                  })).build());
        }

        // 6. Gerenciar Staff (Slot 23)
        open.getPage().addIcon(new IconBuilder(plugin.stack().of("PLAYER_HEAD", 1)
                .customName(mm("<!italic><gradient:#B993D6:#8CA6DB><bold>ɢᴇʀᴇɴᴄɪᴀʀ sᴛᴀғғ</bold></gradient>"))
                .lore(List.of(mm("<!italic><gray>ɢᴇʀᴇɴᴄɪᴇ ᴏs ᴀᴊᴜᴅᴀɴᴛᴇs ᴅᴀ ʟᴏᴊᴀ."), Component.empty(), mm("<!italic><#8CA6DB>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴀʙʀɪʀ.</#8CA6DB>"))))
                .withSlot(23)
                .withActions(new SwitchMenuAction("qs:staff")).build());

        // 7. Histórico (Slot 24)
        open.getPage().addIcon(new IconBuilder(plugin.stack().of("BOOK", 1)
                .customName(mm("<!italic><gradient:#F3904F:#3B4371><bold>ʜɪsᴛóʀɪᴄᴏ ᴅᴇ ᴠᴇɴᴅᴀs</bold></gradient>"))
                .lore(List.of(mm("<!italic><gray>ᴠᴇᴊᴀ ᴀs úʟᴛɪᴍᴀs ᴍᴏᴠɪᴍᴇɴᴛᴀçõᴇs."), Component.empty(), mm("<!italic><#F3904F>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴠᴇʀ ᴏ ʜɪsᴛóʀɪᴄᴏ.</#F3904F>"))))
                .withSlot(24)
                .withActions(new RunnableAction((click)->{
                  viewer.get().close(plugin.createMenuPlayer(player));
                  final List<Shop> shops = List.of(shop.get());
                  final MenuPlayer menuPlayer = plugin.createMenuPlayer(player);
                  Util.asyncThreadRun(()->{
                    final ShopHistory shopHistory = new ShopHistory(plugin, shops);
                    try {
                      final Map<Long, Component> shopHeader = new HashMap<>();
                      shopHeader.put(shop.get().getShopId(), plugin.text().of("history.shop.header-icon-shop-empty-name", shop.get().bukkitLocation().getWorld().getName(), shop.get().bukkitLocation().getBlockX(), shop.get().bukkitLocation().getBlockY(), shop.get().bukkitLocation().getBlockZ()).forLocale());
                      final List<ShopHistory.ShopHistoryRecord> queryResult = shopHistory.query();
                      final ShopHistory.ShopSummary summary = shopHistory.generateSummary().join();
                      if(queryResult == null) return;
                      final Map<Long, DataRecord> dataRecords = new ConcurrentHashMap<>();
                      final List<CompletableFuture<Void>> futures = new ArrayList<>();
                      for(final ShopHistory.ShopHistoryRecord record : queryResult) {
                        futures.add(plugin.getDatabaseHelper().getDataRecord(record.dataId()).thenAccept(data->{
                          if(data != null) dataRecords.put(record.dataId(), data);
                        }));
                      }
                      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                      final MenuViewer historyViewer = new MenuViewer(id);
                      MenuManager.instance().addViewer(historyViewer);
                      historyViewer.addData(SHOPS_DATA, shops);
                      historyViewer.addData(SHOPS_HEADERS, shopHeader);
                      historyViewer.addData(HISTORY_RECORDS, queryResult);
                      historyViewer.addData(HISTORY_DATA_RECORDS, dataRecords);
                      historyViewer.addData(HISTORY_SUMMARY, summary);
                      Util.mainThreadRun(()->MenuManager.instance().open("qs:history", 1, menuPlayer));
                    } catch(final Exception e) {
                      MenuManager.instance().removeViewer(id);
                    }
                  });
                }))
                .withSlot(24).build());

        // 8. Remover Loja (Slot 25)
        open.getPage().addIcon(new IconBuilder(plugin.stack().of("TNT", 1)
                .customName(mm("<!italic><gradient:#FF416C:#8A0000><bold>ʀᴇᴍᴏᴠᴇʀ ʟᴏᴊᴀ</bold></gradient>"))
                .lore(List.of(mm("<!italic><gray>ᴅᴇsᴛʀóɪ ᴇsᴛᴀ ʟᴏᴊᴀ ᴘᴇʀᴍᴀɴᴇɴᴛᴇᴍᴇɴᴛᴇ."), Component.empty(), mm("<!italic><#FF416C>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ʀᴇᴍᴏᴠᴇʀ!</#FF416C>"))))
                .withActions(new GuiChatAction((message)->{
                  if(message.equalsIgnoreCase("confirm")) {
                    Util.regionThread(shop.get().bukkitLocation(), ()->plugin.getShopManager().deleteShop(shop.get()));
                    return true;
                  }
                  return false;
                }, "§c§l[Loja] §eDigite §cconfirm §eno chat para apagar a loja:", false))
                .withSlot(25).build());

        // ==========================================
        // 9. CONFIGURAR CUPONS DESTA LOJA (Slot 26)
        // ==========================================
        final long shopId = shop.get().getShopId();
        open.getPage().addIcon(new IconBuilder(plugin.stack().of("NAME_TAG", 1)
                .customName(mm("<!italic><gradient:#FFE259:#FFA751><bold>ᴄᴏɴғɪɢᴜʀᴀʀ ᴄᴜᴘᴏɴs</bold></gradient>"))
                .lore(List.of(
                        mm("<!italic><gray>ᴄʀɪᴇ ᴏᴜ ɢᴇʀᴇɴᴄɪᴇ ᴄᴜᴘᴏɴs ᴅᴇsᴛᴀ ʟᴏᴊᴀ."),
                        Component.empty(),
                        mm("<!italic><gray>• ᴄʀɪᴀʀ: <#FFE259><ᴄóᴅɪɢᴏ> <ᴘᴏʀᴄᴇɴᴛᴀɢᴇᴍ></#FFE259> (ᴇx: ᴠɪᴘ 20)"),
                        mm("<!italic><gray>• ᴅᴇʟᴇᴛᴀʀ: <#FF416C>ᴅᴇʟᴇᴛᴀʀ <ᴄóᴅɪɢᴏ></#FF416C>"),
                        mm("<!italic><gray>• ᴠᴇʀ ʟɪsᴛᴀ: <#00E5FF>ʟɪsᴛᴀʀ</#00E5FF>"),
                        Component.empty(),
                        mm("<!italic><#FFA751>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ɢᴇʀᴇɴᴄɪᴀʀ!</#FFA751>")
                )))
                .withActions(new GuiChatAction((message) -> {
                  if (!message.isEmpty()) {
                    final String[] parts = message.trim().split("\\s+");
                    // Listar
                    if (parts.length == 1 && (parts[0].equalsIgnoreCase("listar") || parts[0].equalsIgnoreCase("list"))) {
                      final Map<String, Integer> list = ShopCouponManager.getCoupons(shopId);
                      if (list.isEmpty()) {
                        player.sendMessage("§c[Loja] Esta loja não possui nenhum cupom ativo.");
                      } else {
                        player.sendMessage("§6§l[Loja] §eCupons ativos para esta loja:");
                        list.forEach((c, p) -> player.sendMessage("§f• §a" + c + " §7- §e" + p + "% de desconto"));
                      }
                    }
                    // Deletar
                    else if (parts.length >= 2 && (parts[0].equalsIgnoreCase("deletar") || parts[0].equalsIgnoreCase("delete") || parts[0].equalsIgnoreCase("remover"))) {
                      final String code = parts[1].toUpperCase(Locale.ROOT);
                      if (ShopCouponManager.removeCoupon(shopId, code)) {
                        player.sendMessage("§a[Loja] Cupom §e" + code + " §adeletado desta loja com sucesso!");
                      } else {
                        player.sendMessage("§c[Loja] Cupom §e" + code + " §cnão foi encontrado nesta loja.");
                      }
                    }
                    // Criar cupom para esta loja (Ex: VIP 20)
                    else if (parts.length >= 2) {
                      final String code = parts[0].toUpperCase(Locale.ROOT);
                      try {
                        final int percent = Integer.parseInt(parts[1]);
                        if (percent > 0 && percent < 100) {
                          ShopCouponManager.setCoupon(shopId, code, percent);
                          player.sendMessage("§a[Loja] Cupom §e" + code + " §ccom §e" + percent + "% §ade desconto criado exclusivamente para esta loja!");
                        } else {
                          player.sendMessage("§c[Loja] A porcentagem deve ser entre 1% e 99%!");
                        }
                      } catch (NumberFormatException e) {
                        player.sendMessage("§c[Loja] Digite: <código> <porcentagem> (Ex: VIP 20)");
                      }
                    }
                  }
                  Util.regionThread(shop.get().bukkitLocation(), () -> {
                    final MenuPlayer menuPlayer = plugin.createMenuPlayer(player);
                    menuPlayer.inventory().openMenu(menuPlayer, "qs:keeper", KEEPER_MAIN);
                  });
                  return true;
                }, "§6§l[Loja] §eDigite §f<código> <porcentagem> §e(Ex: §fVIP 20§e), §b'listar' §eou §c'deletar <código>'§e:", false))
                .withSlot(26).build());

        // 10. Fechar (Slot 31)
        open.getPage().addIcon(new IconBuilder(plugin.stack().of("OAK_DOOR", 1)
                .customName(mm("<!italic><#E74C3C>ғᴇᴄʜᴀʀ ᴍᴇɴᴜ</#E74C3C>"))
                .lore(List.of(mm("<!italic><gray>ᴠᴏʟᴛᴀʀ ᴀᴏ ᴊᴏɢᴏ."))))
                .withActions(new RunnableAction((click->viewer.get().close(plugin.createMenuPlayer(player)))))
                .withSlot(31).build());
      }
    }
  }
}