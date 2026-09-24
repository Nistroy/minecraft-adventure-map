package io.github.nistroy.adventuremap;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** La carte ne garde rien : elle montre la progression de celui qui l'ouvre, lue sur le serveur. */
public final class AdventureMapItem extends Item {
    public AdventureMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            AdventureMap.service().sendState(serverPlayer, true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.adventuremap.adventurer_map.tooltip").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.adventuremap.adventurer_map.lost").withStyle(ChatFormatting.DARK_GRAY));
    }
}
