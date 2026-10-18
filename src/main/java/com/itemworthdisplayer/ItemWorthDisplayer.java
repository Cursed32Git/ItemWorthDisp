package com.itemworthdisplayer;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.block.ShulkerBox;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public final class ItemWorthDisplayer extends JavaPlugin {
    private static final String MARKER = "§0[ItemWorthDisplayer]";
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.##");
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (Bukkit.getPluginManager().getPlugin("PacketEvents") == null) {
            getLogger().severe("PacketEvents is required. Install PacketEvents 2.14.0 or newer for Minecraft 26.3.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("EconomyShopGUI") == null
                && Bukkit.getPluginManager().getPlugin("EconomyShopGUI-Premium") == null) {
            getLogger().warning("EconomyShopGUI was not detected. Sell values will not be available until it is installed.");
        }

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (!(event.getPlayer() instanceof Player player) || !getConfig().getBoolean("enabled", true)) return;
                try {
                    if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                        WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
                        ItemStack original = SpigotConversionUtil.toBukkitItemStack(packet.getItem());
                        ItemStack processed = addTooltip(player, original);
                        if (!sameItem(original, processed)) {
                            packet.setItem(SpigotConversionUtil.fromBukkitItemStack(processed));
                            event.markForReEncode(true);
                        }
                    } else if (event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
                        WrapperPlayServerSetPlayerInventory packet = new WrapperPlayServerSetPlayerInventory(event);
                        ItemStack original = SpigotConversionUtil.toBukkitItemStack(packet.getStack());
                        ItemStack processed = addTooltip(player, original);
                        if (!sameItem(original, processed)) {
                            packet.setStack(SpigotConversionUtil.fromBukkitItemStack(processed));
                            event.markForReEncode(true);
                        }
                    } else if (event.getPacketType() == PacketType.Play.Server.SET_CURSOR_ITEM) {
                        WrapperPlayServerSetCursorItem packet = new WrapperPlayServerSetCursorItem(event);
                        ItemStack original = SpigotConversionUtil.toBukkitItemStack(packet.getStack());
                        ItemStack processed = addTooltip(player, original);
                        if (!sameItem(original, processed)) {
                            packet.setStack(SpigotConversionUtil.fromBukkitItemStack(processed));
                            event.markForReEncode(true);
                        }
                    } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
                        WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
                        List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = packet.getItems();
                        boolean changed = false;
                        for (int i = 0; i < items.size(); i++) {
                            ItemStack original = SpigotConversionUtil.toBukkitItemStack(items.get(i));
                            ItemStack processed = addTooltip(player, original);
                            if (!sameItem(original, processed)) {
                                items.set(i, SpigotConversionUtil.fromBukkitItemStack(processed));
                                changed = true;
                            }
                        }
                        if (packet.getCarriedItem().isPresent()) {
                            ItemStack original = SpigotConversionUtil.toBukkitItemStack(packet.getCarriedItem().get());
                            ItemStack processed = addTooltip(player, original);
                            if (!sameItem(original, processed)) {
                                packet.setCarriedItem(SpigotConversionUtil.fromBukkitItemStack(processed));
                                changed = true;
                            }
                        }
                        if (changed) {
                            packet.setItems(items);
                            event.markForReEncode(true);
                        }
                    }
                } catch (Throwable ex) {
                    // Never interrupt packet delivery because a third-party item/API could not be processed.
                    getLogger().fine("Could not add sell tooltip: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }
        });
        getLogger().info("ItemWorthDisplayer enabled: client-side sell-price tooltips active.");
    }

    private boolean sameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        return a.equals(b) && a.getAmount() == b.getAmount();
    }

    private ItemStack addTooltip(Player player, ItemStack source) {
        if (source == null || source.getType().isAir()) return source;
        ItemStack result = source.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line != null && line.contains(MARKER));

        Value value = calculateValue(player, result, 0);
        if (!value.hasAnyPrice) {
            meta.setLore(lore.isEmpty() ? null : lore);
            result.setItemMeta(meta);
            return result;
        }

        lore.add(color(getConfig().getString("total-format", "&6Total value: &f$%price%")
                .replace("%price%", moneyFormat.format(value.total))));
        lore.add(MARKER);
        meta.setLore(lore);
        result.setItemMeta(meta);
        return result;
    }

    private Value calculateValue(Player player, ItemStack item, int depth) {
        Value result = new Value();
        if (item == null || item.getType().isAir() || depth > getConfig().getInt("max-container-depth", 4)) return result;

        Double ownPrice = getSellPrice(player, item);
        if (ownPrice != null && ownPrice >= 0) {
            result.total += ownPrice;
            result.hasAnyPrice = true;
        }

        for (ItemStack content : getContainerItems(item)) {
            if (content == null || content.getType().isAir()) continue;
            Value child = calculateValue(player, content, depth + 1);
            if (child.total > 0) {
                result.total += child.total;
                result.hasAnyPrice = true;
            }
        }
        return result;
    }

    private List<ItemStack> getContainerItems(ItemStack item) {
        List<ItemStack> contents = new ArrayList<>();
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BlockStateMeta blockMeta) {
                Object state = blockMeta.getBlockState();
                if (state instanceof ShulkerBox shulker) {
                    for (ItemStack stack : shulker.getInventory().getContents()) if (stack != null && !stack.getType().isAir()) contents.add(stack);
                } else if (state instanceof InventoryHolder holder) {
                    Inventory inventory = holder.getInventory();
                    for (ItemStack stack : inventory.getContents()) if (stack != null && !stack.getType().isAir()) contents.add(stack);
                }
            }
            // BundleMeta#getItems is accessed reflectively to stay tolerant of API changes.
            if (meta != null && meta.getClass().getSimpleName().equals("CraftMetaBundle")) {
                Method method = meta.getClass().getMethod("getItems");
                Object value = method.invoke(meta);
                if (value instanceof Iterable<?> iterable) {
                    for (Object entry : iterable) if (entry instanceof ItemStack stack && !stack.getType().isAir()) contents.add(stack);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Unsupported container metadata is skipped safely.
        }
        return contents;
    }

    private Double getSellPrice(Player player, ItemStack item) {
        try {
            Class<?> hook = Class.forName("me.gypopo.economyshopgui.api.EconomyShopGUIHook");
            Object shopItem = null;
            for (Method method : hook.getMethods()) {
                if (!method.getName().equals("getShopItem") || method.getParameterCount() != 2) continue;
                Class<?>[] p = method.getParameterTypes();
                if (p[0].isAssignableFrom(player.getClass()) && p[1].isAssignableFrom(item.getClass())) {
                    shopItem = method.invoke(null, player, item);
                    break;
                }
            }
            if (shopItem == null) return null;
            for (Method method : hook.getMethods()) {
                if (!method.getName().equals("getItemSellPrice") || method.getParameterCount() != 4) continue;
                Class<?>[] p = method.getParameterTypes();
                Object[] args = new Object[4];
                boolean foundShop = false, foundPlayer = false, foundItem = false, foundAmount = false;
                for (int i = 0; i < p.length; i++) {
                    if (!foundShop && p[i].isInstance(shopItem)) { args[i] = shopItem; foundShop = true; }
                    else if (!foundPlayer && p[i].isAssignableFrom(player.getClass())) { args[i] = player; foundPlayer = true; }
                    else if (!foundItem && p[i].isAssignableFrom(item.getClass())) { args[i] = item; foundItem = true; }
                    else if ((p[i] == int.class || p[i] == Integer.class) && !foundAmount) { args[i] = item.getAmount(); foundAmount = true; }
                }
                if (foundShop && foundPlayer && foundItem && foundAmount) {
                    Object price = method.invoke(null, args);
                    if (price instanceof Number number) return number.doubleValue();
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // ESGUI API versions vary; a missing method simply means no price can be shown.
        }
        return null;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private static final class Value {
        private double total;
        private boolean hasAnyPrice;
    }
}
