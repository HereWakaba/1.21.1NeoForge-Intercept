package com.ryjs.intercept.item;

import com.ryjs.intercept.client.effect.TaiChiChargeEffect;
import com.ryjs.intercept.util.TaiChiName;
import com.ryjs.intercept.util.timestop.TimestopTextExempt;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;


@SuppressWarnings("removal")
public class EndOfTaiChi extends SwordItem implements TimestopTextExempt {

    public static final int MAX_USE_TICKS = 72000;
    public static final int CHARGE_START_TICKS = 2;

    private static final ResourceLocation REACH_ID =
            com.ryjs.intercept.Intercept.rl("taichi_reach");
    private static final ResourceLocation BLOCK_REACH_ID =
            com.ryjs.intercept.Intercept.rl("taichi_block_reach");

    public EndOfTaiChi() {
        super(Tiers.NETHERITE, new Item.Properties()
                .attributes(createTaiChiAttributes())
                .stacksTo(1)
                .fireResistant()
                .rarity(Rarity.EPIC));
    }

    private static ItemAttributeModifiers createTaiChiAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                                3.0D + Tiers.NETHERITE.getAttackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.4D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(REACH_ID, 2147483647.0D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.BLOCK_INTERACTION_RANGE,
                        new AttributeModifier(BLOCK_REACH_ID, 1638465536.0D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return TaiChiName.asComponent(System.currentTimeMillis());
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                          @NotNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return MAX_USE_TICKS;
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack,
                          int remainingUseDuration) {
        if (!level.isClientSide || !(entity instanceof Player player)) return;
        int charged = getUseDuration(stack, entity) - remainingUseDuration;
        if (charged >= CHARGE_START_TICKS) {
            TaiChiChargeEffect.setFullPause(player.isShiftKeyDown());
        }
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity, int timeLeft) {
        if (level.isClientSide) {
            TaiChiChargeEffect.setFullPause(false);
        }
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.CUSTOM;
    }

    @Override
    public void initializeClient(
            @NotNull java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        com.ryjs.intercept.client.HandTransform.register(consumer);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
    }
}
