package ru.blockjackpot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class BlockJackpot implements ModInitializer {
    public static final String MOD_ID = "blockjackpot";
    private static final Map<UUID, Integer> BALANCES = new ConcurrentHashMap<>();

    public static int balance(ServerPlayerEntity player) {
        return BALANCES.computeIfAbsent(player.getUuid(), id -> 500);
    }

    public static void addCoins(ServerPlayerEntity player, int amount) {
        BALANCES.put(player.getUuid(), Math.max(0, balance(player) + amount));
    }

    public static boolean charge(ServerPlayerEntity player, int amount) {
        int current = balance(player);
        if (current < amount) return false;
        BALANCES.put(player.getUuid(), current - amount);
        return true;
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("bj")
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    p.sendMessage(Text.literal("§6§l✦ BLOCK JACKPOT ✦"), false);
                    p.sendMessage(Text.literal("§eБаланс: §6" + balance(p) + " монет"), false);
                    p.sendMessage(Text.literal("§a/bj shop — магазин"), false);
                    p.sendMessage(Text.literal("§d/bj roulette <ставка> — рулетка"), false);
                    p.sendMessage(Text.literal("§b/bj sell — продать один алмаз/железо/золото/изумруд"), false);
                    p.sendMessage(Text.literal("§e/bj bonus — бонус раз в 20 минут реального времени (тестовая версия)"), false);
                    return 1;
                })
                .then(literal("balance").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    p.sendMessage(Text.literal("§eТвой баланс: §6" + balance(p) + " монет"), false);
                    return 1;
                }))
                .then(literal("bonus").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    long now = System.currentTimeMillis();
                    long last = BONUS_TIMES.getOrDefault(p.getUuid(), 0L);
                    if (now - last < 20 * 60 * 1000L) {
                        p.sendMessage(Text.literal("§cБонус уже получен. Подожди ещё немного."), false);
                        return 0;
                    }
                    BONUS_TIMES.put(p.getUuid(), now);
                    addCoins(p, 250);
                    p.sendMessage(Text.literal("§6+250 монет! §eБаланс: " + balance(p)), false);
                    return 1;
                }))
                .then(literal("roulette").then(argument("bet", IntegerArgumentType.integer(1, 100000))
                    .executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        int bet = IntegerArgumentType.getInteger(ctx, "bet");
                        if (!charge(p, bet)) {
                            p.sendMessage(Text.literal("§cНе хватает монет! Баланс: " + balance(p)), false);
                            return 0;
                        }
                        int roll = ThreadLocalRandom.current().nextInt(100) + 1;
                        if (roll <= 5) {
                            addCoins(p, bet * 10);
                            p.sendMessage(Text.literal("§6§lДЖЕКПОТ! §eВыпало " + roll + "/100. Выплата: " + (bet * 10) + " монет."), false);
                        } else if (roll <= 25) {
                            addCoins(p, bet * 2);
                            p.sendMessage(Text.literal("§aВЫИГРЫШ! §eВыпало " + roll + "/100. Выплата: " + (bet * 2) + " монет."), false);
                        } else {
                            p.sendMessage(Text.literal("§cНе повезло! Выпало " + roll + "/100. Ставка потеряна."), false);
                        }
                        return 1;
                    })))
                .then(literal("shop").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    p.sendMessage(Text.literal("§a§lМАГАЗИН §7— покупка через команды:"), false);
                    p.sendMessage(Text.literal("§f/bj buy iron — 16 железа за 100"), false);
                    p.sendMessage(Text.literal("§b/bj buy diamond — алмаз за 250"), false);
                    p.sendMessage(Text.literal("§6/bj buy apple — золотое яблоко за 400"), false);
                    p.sendMessage(Text.literal("§d/bj buy titan — Палка Титана за 1500"), false);
                    return 1;
                }))
                .then(literal("buy").then(literal("iron").executes(ctx -> buy(ctx.getSource().getPlayerOrThrow(), 100, "iron"))))
                .then(literal("buy").then(literal("diamond").executes(ctx -> buy(ctx.getSource().getPlayerOrThrow(), 250, "diamond"))))
                .then(literal("buy").then(literal("apple").executes(ctx -> buy(ctx.getSource().getPlayerOrThrow(), 400, "apple"))))
                .then(literal("buy").then(literal("titan").executes(ctx -> buy(ctx.getSource().getPlayerOrThrow(), 1500, "titan"))))
                .then(literal("sell").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    String[] ids = {"minecraft:iron_ingot","minecraft:gold_ingot","minecraft:diamond","minecraft:emerald"};
                    int[] prices = {10,25,100,35};
                    for (int i = 0; i < ids.length; i++) {
                        var item = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(ids[i]));
                        var stack = new net.minecraft.item.ItemStack(item);
                        if (p.getInventory().contains(stack)) {
                            p.getInventory().remove(stack);
                            addCoins(p, prices[i]);
                            p.sendMessage(Text.literal("§aПродано: +" + prices[i] + " монет. Баланс: " + balance(p)), false);
                            return 1;
                        }
                    }
                    p.sendMessage(Text.literal("§cВ инвентаре нет ресурсов для продажи."), false);
                    return 0;
                }))
            );
        });
    }

    private static final Map<UUID, Long> BONUS_TIMES = new ConcurrentHashMap<>();

    private static int buy(ServerPlayerEntity p, int price, String item) {
        if (!charge(p, price)) {
            p.sendMessage(Text.literal("§cНе хватает монет. Нужно " + price + "."), false);
            return 0;
        }
        switch (item) {
            case "iron" -> p.giveItemStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_INGOT, 16));
            case "diamond" -> p.giveItemStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND));
            case "apple" -> p.giveItemStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.GOLDEN_APPLE));
            case "titan" -> {
                var stick = new net.minecraft.item.ItemStack(net.minecraft.item.Items.STICK);
                stick.addEnchantment(p.getServer().getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
                    .getOrThrow(net.minecraft.enchantment.Enchantments.KNOCKBACK), 5);
                p.giveItemStack(stick);
            }
        }
        p.sendMessage(Text.literal("§aПокупка успешна! §eБаланс: " + balance(p)), false);
        return 1;
    }
}
