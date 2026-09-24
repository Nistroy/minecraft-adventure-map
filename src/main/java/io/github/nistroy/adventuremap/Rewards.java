package io.github.nistroy.adventuremap;

import io.github.nistroy.adventuremap.map.Reward;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Transforme une récompense en objet réel. Sert au serveur (don) et au client (aperçu dans l'écran). */
public final class Rewards {
    private Rewards() {}

    /** Objet vide si l'identifiant n'existe pas (mod absent) : signalé au démarrage du serveur. */
    public static ItemStack toStack(Reward reward, RegistryAccess registries) {
        ResourceLocation id = ResourceLocation.tryParse(reward.item());
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(id).map(item -> {
            ItemStack stack = new ItemStack(item, reward.count());
            if (reward.unique()) {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(reward.name())
                        .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
                stack.set(DataComponents.RARITY, Rarity.EPIC);
                List<Component> lore = reward.lore().stream()
                        .map(line -> (Component) Component.literal(line).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC))
                        .toList();
                stack.set(DataComponents.LORE, new ItemLore(lore));
            }
            if (!reward.enchantments().isEmpty()) {
                stack.set(DataComponents.ENCHANTMENTS, enchantments(reward.enchantments(), registries));
            }
            return stack;
        }).orElse(ItemStack.EMPTY);
    }

    private static ItemEnchantments enchantments(Map<String, Integer> levels, RegistryAccess registries) {
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        levels.forEach((id, level) -> {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location != null) registry.getHolder(location).ifPresent(holder -> mutable.set(holder, level));
        });
        return mutable.toImmutable();
    }
}
