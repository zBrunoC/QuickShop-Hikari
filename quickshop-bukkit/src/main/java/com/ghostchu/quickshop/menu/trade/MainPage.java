package com.ghostchu.quickshop.menu.trade;
/*
 * QuickShop-Hikari
 * Copyright (C) 2024 Daniel "creatorfromhell" Vidmar
 */

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.economy.EconomyProvider;
import com.ghostchu.quickshop.api.event.display.ItemPreviewComponentPrePopulateEvent;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Info;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.ShopAction;
import com.ghostchu.quickshop.config.GuiConfig;
import com.ghostchu.quickshop.menu.browse.MarketUtils;
import com.ghostchu.quickshop.menu.shared.GuiChatAction;
import com.ghostchu.quickshop.menu.shared.PageSwitchWithCloseAction;
import com.ghostchu.quickshop.menu.shared.QuickShopPage;
import com.ghostchu.quickshop.obj.QUserImpl;
import com.ghostchu.quickshop.shop.SimpleInfo;
import com.ghostchu.quickshop.shop.inventory.BukkitInventoryWrapper;
import com.ghostchu.quickshop.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.tnemc.item.AbstractItemStack;
import net.tnemc.item.providers.SkullProfile;
import net.tnemc.menu.core.builder.IconBuilder;
import net.tnemc.menu.core.callbacks.page.PageOpenCallback;
import net.tnemc.menu.core.compatibility.MenuPlayer;
import net.tnemc.menu.core.icon.action.impl.RunnableAction;
import net.tnemc.menu.core.manager.MenuManager;
import net.tnemc.menu.core.viewer.MenuViewer;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.ghostchu.quickshop.menu.ShopKeeperMenu.SHOP_STOCK_ID;

public class MainPage extends QuickShopPage {

  private static final Map<UUID, Shop> CURRENT_SHOPS = new ConcurrentHashMap<>();
  private static final Map<UUID, Integer> SELECTED_QUANTITY = new ConcurrentHashMap<>();
  private static final Map<UUID, String> APPLIED_COUPONS = new ConcurrentHashMap<>();

  private static Component mm(String text) {
    return MiniMessage.miniMessage().deserialize(text);
  }

  public MainPage() {
    super(1);
    setOpen(this::handle);
  }

  public void handle(final PageOpenCallback open) {
    open.getPage().getIcons().clear();

    final UUID id = open.getPlayer().identifier();
    final Player player = Bukkit.getPlayer(id);
    if(player == null) return;

    final QuickShop plugin = QuickShop.getInstance();

    Optional<Shop> shopOpt = Optional.empty();
    final Optional<MenuViewer> viewerOpt = open.getPlayer().viewer();
    if(viewerOpt.isPresent()) {
      shopOpt = getShop(viewerOpt.get());
    }
    if(shopOpt.isEmpty()) {
      shopOpt = Optional.ofNullable(CURRENT_SHOPS.get(id));
    }
    if(shopOpt.isEmpty()) {
      final Block target = player.getTargetBlockExact(6);
      if(target != null) {
        shopOpt = Optional.ofNullable(plugin.getShopManager().getShop(target.getLocation()));
      }
    }
    if(shopOpt.isEmpty()) {
      return;
    }

    final Shop shop = shopOpt.get();
    CURRENT_SHOPS.put(id, shop);

    MenuViewer viewer = viewerOpt.orElse(null);
    if(viewer == null) {
      viewer = MenuManager.instance().findViewer(id).orElseGet(() -> {
        final MenuViewer v = new MenuViewer(id);
        MenuManager.instance().addViewer(v);
        return v;
      });
    }

    final EconomyProvider eco = plugin.getEconomyManager().provider();
    final String worldName = shop.bukkitLocation().getWorld().getName();
    final String currency = shop.getCurrency();

    final ItemStack shopItem = shop.getItem();
    final int amount = shopItem.getAmount();
    final int remainingStock = (int) viewer.dataOrDefault(SHOP_STOCK_ID, MarketUtils.getStockFromCache(shop));

    int selectedQuantity = SELECTED_QUANTITY.computeIfAbsent(id, k -> 1);
    if (!shop.isUnlimited() && remainingStock > 0 && selectedQuantity > remainingStock) {
      selectedQuantity = remainingStock;
      SELECTED_QUANTITY.put(id, selectedQuantity);
    }

    // CÁLCULO DO DESCONTO NATIVO
    final String appliedCode = APPLIED_COUPONS.get(id);
    final int discountPercent = ShopCouponManager.getDiscount(shop.getShopId(), appliedCode);

    final double baseUnitPrice = shop.getPrice();
    final double discountedUnitPrice = (discountPercent > 0) ? (baseUnitPrice * (1.0 - (discountPercent / 100.0))) : baseUnitPrice;
    final double baseTotalPrice = baseUnitPrice * selectedQuantity;
    final double discountedTotalPrice = discountedUnitPrice * selectedQuantity;

    // Formatação de Preços usando BigDecimal e World conforme a interface EconomyProvider
    final String unitPriceFormatted = eco.format(BigDecimal.valueOf(baseUnitPrice), worldName, currency);
    final String discountedTotalFormatted = eco.format(BigDecimal.valueOf(discountedTotalPrice), worldName, currency);

    // 1. Item da Loja (Slot 13)
    final ItemPreviewComponentPrePopulateEvent previewComponentPrePopulateEvent = new ItemPreviewComponentPrePopulateEvent(shopItem, player);
    previewComponentPrePopulateEvent.callEvent();
    final AbstractItemStack<ItemStack> shopItemStack = plugin.stack(previewComponentPrePopulateEvent.getItemStack());
    open.getPage().addIcon(new IconBuilder(shopItemStack).withSlot(13).build());

    // 2. Estoque (Slot 21)
    final Component stockComponent = switch (remainingStock) {
      case -1 -> plugin.text().of(player, "signs.unlimited").forLocale();
      case 0 -> mm("<red>Esgotado</red>");
      default -> Component.text(remainingStock);
    };
    open.getPage().addIcon(new IconBuilder(plugin.stack().of("CHEST", 1)
            .customName(mm("<!italic><gradient:#00C9FF:#92FE9D><bold>ᴇsᴛᴏǫᴜᴇ ᴅɪsᴘᴏɴíᴠᴇʟ</bold></gradient>"))
            .lore(getConfigLore(id, plugin.getGuiConfig().getMenuConfig("trade").getIcon("info-stock"), stockComponent)))
            .withSlot(21).build());

    // 3. Resumo da Compra (Slot 22 - Barra de Ouro com Atualização Visual Instantânea)
    final List<Component> priceLore;
    if (discountPercent > 0) {
      priceLore = List.of(
              mm("<!italic><gray>> ᴘʀᴇçᴏ ᴏʀɪɢɪɴᴀʟ: <st><#FFE259>" + unitPriceFormatted + "</#FFE259></st>"),
              mm("<!italic><gray>> ᴄᴜᴘᴏᴍ: <#00E5FF>" + appliedCode + " (-" + discountPercent + "%)</#00E5FF>"),
              mm("<!italic><gray>> ǫᴜᴀɴᴛɪᴅᴀᴅᴇ: <white>" + (selectedQuantity * amount) + "</white>"),
              mm("<!italic><gray>► ᴛᴏᴛᴀʟ ᴄᴏᴍ ᴅᴇsᴄᴏɴᴛᴏ: <#55FF55>" + discountedTotalFormatted + "</#55FF55>")
      );
    } else {
      priceLore = List.of(
              mm("<!italic><gray>> ᴘʀᴇçᴏ ᴜɴɪᴛáʀɪᴏ: <#FFE259>" + unitPriceFormatted + "</#FFE259>"),
              mm("<!italic><gray>> ǫᴜᴀɴᴛɪᴅᴀᴅᴇ: <white>" + (selectedQuantity * amount) + "</white>"),
              mm("<!italic><gray>► ᴛᴏᴛᴀʟ: <#FFE259>" + discountedTotalFormatted + "</#FFE259>")
      );
    }

    open.getPage().addIcon(new IconBuilder(plugin.stack().of("GOLD_INGOT", 1)
            .customName(mm("<!italic><gradient:#FFE259:#FFA751><bold>ʀᴇsᴜᴍᴏ ᴅᴀ ᴄᴏᴍᴘʀᴀ</bold></gradient>"))
            .lore(priceLore))
            .withSlot(22).build());

    // 4. Dono da Loja (Slot 23)
    SkullProfile sellerProfile = null;
    if(shop.getOwner().isRealPlayer()) {
      sellerProfile = new SkullProfile();
      sellerProfile.uuid(shop.getOwner().getUniqueId());
    }
    open.getPage().addIcon(new IconBuilder(plugin.stack().of("PLAYER_HEAD", 1)
            .customName(mm("<!italic><gradient:#A8BBA2:#5D7A68><bold>ᴅᴏɴᴏ ᴅᴀ ʟᴏᴊᴀ</bold></gradient>"))
            .lore(getConfigLore(id, plugin.getGuiConfig().getMenuConfig("trade").getIcon("info-seller"), shop.getOwner().getDisplay()))
            .profile(sellerProfile))
            .withSlot(23).build());

    // Botão -10 (Slot 29)
    open.getPage().addIcon(new IconBuilder(plugin.stack().of("RED_CONCRETE", 1)
            .customName(mm("<!italic><gradient:#FF416C:#8A0000><bold>- 10</bold></gradient>"))
            .lore(List.of(mm("<!italic><gray>ʀᴇᴍᴏᴠᴇʀ <#FF416C>10</#FF416C> ᴅᴀ ǫᴜᴀɴᴛɪᴅᴀᴅᴇ."))))
            .withActions(new RunnableAction(click -> {
              SELECTED_QUANTITY.put(id, Math.max(1, SELECTED_QUANTITY.getOrDefault(id, 1) - 10));
            }))
            .withActions(new PageSwitchWithCloseAction("qs:trade", 1))
            .withSlot(29).build());

    // Botão -1 (Slot 30)
    open.getPage().addIcon(new IconBuilder(plugin.stack().of("RED_DYE", 1)
            .customName(mm("<!italic><gradient:#FF416C:#8A0000><bold>- 1</bold></gradient>"))
            .lore(List.of(mm("<!italic><gray>ʀᴇᴍᴏᴠᴇʀ <#FF416C>1</#FF416C> ᴅᴀ ǫᴜᴀɴᴛɪᴅᴀᴅᴇ."))))
            .withActions(new RunnableAction(click -> {
              SELECTED_QUANTITY.put(id, Math.max(1, SELECTED_QUANTITY.getOrDefault(id, 1) - 1));
            }))
            .withActions(new PageSwitchWithCloseAction("qs:trade", 1))
            .withSlot(30).build());

    // Papel Central (Slot 31)
    open.getPage().addIcon(new IconBuilder(plugin.stack().of("PAPER", Math.min(Math.max(1, selectedQuantity), 64))
            .customName(mm("<!italic><#00E5FF><bold>ǫᴜᴀɴᴛɪᴅᴀᴅᴇ: " + (selectedQuantity * amount) + "</bold></#00E5FF>"))
            .lore(List.of(
                    mm("<!italic><gray>ᴜsᴇ ᴏs ʙᴏᴛõᴇs ᴅᴏs ʟᴀᴅᴏs ᴏᴜ"),
                    mm("<!italic><#00E5FF>ᴄʟɪǫᴜᴇ ᴀǫᴜɪ ᴘᴀʀᴀ ᴅɪɢɪᴛᴀʀ ᴜᴍ ᴠᴀʟᴏʀ.</#00E5FF>")
            )))
            .withActions(new GuiChatAction((message) -> {
              if(!message.isEmpty()) {
                try {
                  final int typed = Integer.parseInt(message);
                  if (typed > 0) {
                    int normalized = typed / amount;
                    if (normalized < 1) normalized = 1;
                    if (!shop.isUnlimited() && remainingStock > 0 && normalized > remainingStock) {
                      normalized = remainingStock;
                    }
                    SELECTED_QUANTITY.put(id, normalized);
                  }
                } catch (NumberFormatException ignored) {}
              }
              Util.regionThread(shop.bukkitLocation(), () -> {
                final MenuPlayer menuPlayer = plugin.createMenuPlayer(player);
                final MenuViewer v = new MenuViewer(id);
                v.addData(SHOP_STOCK_ID, MarketUtils.getStockFromCache(shop));
                MenuManager.instance().addViewer(v);
                MenuManager.instance().open("qs:trade", 1, menuPlayer);
              });
              return true;
            }, "§6§l[Loja] §eDigite a quantidade desejada:", false))
            .withSlot(31).build());

    // Botão +1 (Slot 32)
    open.getPage().addIcon(new IconBuilder(plugin.stack().of("LIME_DYE", 1)
            .customName(mm("<!italic><gradient:#56AB2F:#A8E063><bold>+ 1</bold></gradient>"))
            .lore(List.of(mm("<!italic><gray>ᴀᴅɪᴄɪᴏɴᴀʀ <#A8E063>1</#A8E063> à ǫᴜᴀɴᴛɪᴅᴀᴅᴇ."))))
            .withActions(new RunnableAction(click -> {
              int newQty = SELECTED_QUANTITY.getOrDefault(id, 1) + 1;
              if (!shop.isUnlimited() && remainingStock > 0 && newQty > remainingStock) {
                newQty = remainingStock;
              }
              SELECTED_QUANTITY.put(id, newQty);
            }))
            .withActions(new PageSwitchWithCloseAction("qs:trade", 1))
            .withSlot(32).build());

    // Botão +10 (Slot 33)
    open.getPage().addIcon(new IconBuilder(plugin.stack().of("LIME_CONCRETE", 1)
            .customName(mm("<!italic><gradient:#56AB2F:#A8E063><bold>+ 10</bold></gradient>"))
            .lore(List.of(mm("<!italic><gray>ᴀᴅɪᴄɪᴏɴᴀʀ <#A8E063>10</#A8E063> à ǫᴜᴀɴᴛɪᴅᴀᴅᴇ."))))
            .withActions(new RunnableAction(click -> {
              int newQty = SELECTED_QUANTITY.getOrDefault(id, 1) + 10;
              if (!shop.isUnlimited() && remainingStock > 0 && newQty > remainingStock) {
                newQty = remainingStock;
              }
              SELECTED_QUANTITY.put(id, newQty);
            }))
            .withActions(new PageSwitchWithCloseAction("qs:trade", 1))
            .withSlot(33).build());

    // 5. Botão de Cupom (Slot 40 - Etiqueta)
    final List<Component> couponLore;
    if (discountPercent > 0) {
      couponLore = List.of(
              mm("<!italic><#55FF55>• ᴄᴜᴘᴏᴍ ᴀᴛɪᴠᴏ: <white>" + appliedCode + "</white> (-" + discountPercent + "%)</#55FF55>"),
              Component.empty(),
              mm("<!italic><#FFA751>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴛʀᴏᴄᴀʀ ᴏᴜ ʀᴇᴍᴏᴠᴇʀ.</#FFA751>")
      );
    } else {
      couponLore = List.of(
              mm("<!italic><gray>ᴘᴏssᴜɪ ᴜᴍ ᴄᴜᴘᴏᴍ ᴅᴇsᴛᴀ ʟᴏᴊᴀ?"),
              Component.empty(),
              mm("<!italic><#FFA751>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ɪɴsᴇʀɪʀ ᴏ ᴄóᴅɪɢᴏ.</#FFA751>")
      );
    }

    open.getPage().addIcon(new IconBuilder(plugin.stack().of("NAME_TAG", 1)
            .customName(mm("<!italic><gradient:#FFE259:#FFA751><bold>ᴀᴘʟɪᴄᴀʀ ᴄᴜᴘᴏᴍ</bold></gradient>"))
            .lore(couponLore))
            .withActions(new GuiChatAction((codeMessage) -> {
              if (!codeMessage.isEmpty()) {
                final String code = codeMessage.trim().toUpperCase(Locale.ROOT);
                final int disc = ShopCouponManager.getDiscount(shop.getShopId(), code);
                if (disc > 0) {
                  APPLIED_COUPONS.put(id, code);
                  player.sendMessage("§a[Loja] Cupom §e" + code + " §aaplicado! Você recebeu §e" + disc + "% §ade desconto nesta loja!");
                } else {
                  APPLIED_COUPONS.remove(id);
                  player.sendMessage("§c[Loja] O cupom §e" + code + " §cnão existe ou não é válido para esta loja.");
                }
              }

              Util.regionThread(shop.bukkitLocation(), () -> {
                final MenuPlayer menuPlayer = plugin.createMenuPlayer(player);
                final MenuViewer v = new MenuViewer(id);
                v.addData(SHOP_STOCK_ID, MarketUtils.getStockFromCache(shop));
                MenuManager.instance().addViewer(v);
                MenuManager.instance().open("qs:trade", 1, menuPlayer);
              });
              return true;
            }, "§6§l[Loja] §eDigite o código do cupom oferecido por esta loja:", false))
            .withSlot(40).build());

    // 6. Botão Cancelar (Slot 47)
    open.getPage().addIcon(new IconBuilder(plugin.stack().of("BARRIER", 1)
            .customName(mm("<!italic><gradient:#FF416C:#8A0000><bold>ᴄᴀɴᴄᴇʟᴀʀ</bold></gradient>"))
            .lore(List.of(mm("<!italic><gray>ғᴇᴄʜᴀʀ sᴇᴍ ᴄᴏᴍᴘʀᴀʀ."))))
            .withActions(new RunnableAction((click -> {
              SELECTED_QUANTITY.remove(id);
              APPLIED_COUPONS.remove(id);
              CURRENT_SHOPS.remove(id);
              player.closeInventory();
            })))
            .withSlot(47).build());

    // 7. Botão Confirmar Compra (Slot 51 - Esmeralda)
    final String actionTitle = shop.isSelling() ? "ᴄᴏɴғɪʀᴍᴀʀ ᴄᴏᴍᴘʀᴀ" : "ᴄᴏɴғɪʀᴍᴀʀ ᴠᴇɴᴅᴀ";
    final String actionGradient = shop.isSelling() ? "<gradient:#00FF88:#00AA55>" : "<gradient:#F2994A:#F2C94C>";

    final Component itemLine = Component.text()
            .decoration(TextDecoration.ITALIC, false)
            .append(mm("<!italic><gray>> <white>ɪᴛᴇᴍ: "))
            .append(Util.getItemStackName(shopItem).colorIfAbsent(NamedTextColor.AQUA))
            .build();

    final List<Component> confirmLore = new ArrayList<>();
    confirmLore.add(itemLine);
    confirmLore.add(mm("<!italic><gray>> <white>ǫᴜᴀɴᴛɪᴅᴀᴅᴇ: <yellow>" + (selectedQuantity * amount) + "</yellow>"));
    if (discountPercent > 0) {
      confirmLore.add(mm("<!italic><gray>> <white>ᴛᴏᴛᴀʟ: <#55FF55>" + discountedTotalFormatted + " </#55FF55><#00E5FF>(-" + discountPercent + "% OFF)</#00E5FF>"));
    } else {
      confirmLore.add(mm("<!italic><gray>> <white>ᴛᴏᴛᴀʟ: <#FFD700>" + discountedTotalFormatted + "</#FFD700>"));
    }
    confirmLore.add(Component.empty());
    confirmLore.add(mm("<!italic><#55FF55>ᴄʟɪǫᴜᴇ ᴘᴀʀᴀ ᴄᴏɴғɪʀᴍᴀʀ.</#55FF55>"));

    open.getPage().addIcon(new IconBuilder(plugin.stack().of("EMERALD_BLOCK", 1)
            .customName(mm("<!italic>" + actionGradient + "<bold>" + actionTitle + "</bold></gradient>"))
            .lore(confirmLore))
            .withActions(new RunnableAction((click -> {
              final int finalQty = SELECTED_QUANTITY.getOrDefault(id, 1);

              if (shop.getOwner().getUniqueId().equals(player.getUniqueId()) && !player.isOp() && !player.hasPermission("quickshop.bypass.self-trade")) {
                player.sendMessage(mm("<!italic><red>[Loja] Você não pode comprar na sua própria loja!</red>"));
                return;
              }

              final double discountAmount = (discountPercent > 0) ? (baseTotalPrice * (discountPercent / 100.0)) : 0.0;
              final double finalToPay = baseTotalPrice - discountAmount;
              final QUser buyerUser = QUserImpl.createFullFilled(player);
              final BigDecimal finalToPayBD = BigDecimal.valueOf(finalToPay);

              // Checa saldo do comprador usando balance(...) do EconomyProvider
              if (shop.isSelling() && eco.balance(buyerUser, worldName, currency).compareTo(finalToPayBD) < 0) {
                player.sendMessage(mm("<!italic><red>[Loja] Saldo insuficiente! Você precisa de " + discountedTotalFormatted + ".</red>"));
                return;
              }

              // Executa a transação com compensação do desconto
              if(shop.isBuying()) {
                final Info info = new SimpleInfo(shop.bukkitLocation(), ShopAction.PURCHASE_SELL, null, null, shop, false);
                Util.regionThread(shop.bukkitLocation(), () -> plugin.getShopManager().actionBuying(player, new BukkitInventoryWrapper(player.getInventory()), eco, info, shop, finalQty));
              } else {
                final Info info = new SimpleInfo(shop.bukkitLocation(), ShopAction.PURCHASE_BUY, null, null, shop, false);
                Util.regionThread(shop.bukkitLocation(), () -> {
                  // Se houver desconto, compensa o comprador antes da cobrança cheia
                  if (discountAmount > 0) {
                    eco.deposit(buyerUser, worldName, currency, BigDecimal.valueOf(discountAmount));
                  }

                  plugin.getShopManager().actionSelling(player, new BukkitInventoryWrapper(player.getInventory()), eco, info, shop, finalQty);

                  // Retira a diferença do dono da loja (já que o desconto é benefício concedido por ele)
                  if (discountAmount > 0 && shop.getOwner().isRealPlayer()) {
                    eco.withdraw(shop.getOwner(), worldName, currency, BigDecimal.valueOf(discountAmount));
                    final String savedFormatted = eco.format(BigDecimal.valueOf(discountAmount), worldName, currency);
                    player.sendMessage(mm("<!italic><#55FF55>[Loja] Você economizou <yellow>" + savedFormatted + "</yellow> com o cupom <yellow>" + appliedCode + "</yellow>!</#55FF55>"));
                  }
                });
              }

              SELECTED_QUANTITY.remove(id);
              APPLIED_COUPONS.remove(id);
              CURRENT_SHOPS.remove(id);
              player.closeInventory();
            })))
            .withSlot(51).build());
  }
}