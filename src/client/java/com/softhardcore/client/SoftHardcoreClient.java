package com.softhardcore.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

import com.softhardcore.SoftHardcore;

public class SoftHardcoreClient implements ClientModInitializer {
  private static final ResourceLocation BROKEN_HEART = ResourceLocation.fromNamespaceAndPath(SoftHardcore.MOD_ID,
      "hud/broken_heart");

  private static int lastHealth = -1;
  private static long blinkEnd = 0;

  @Override
  public void onInitializeClient() {
    HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR,
        previous -> (graphics, deltaTracker) -> render(graphics));
  }

  private static int lostHearts(Player player) {
    AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);

    if (attr == null) {
      return 0;
    }

    AttributeModifier mod = attr.getModifier(SoftHardcore.MODIFIER_ID);

    if (mod == null) {
      return 0;
    }

    return Math.max(0, (int) Math.round(-mod.amount() / 2.0));
  }

  private static ResourceLocation vanilla(String name) {
    return ResourceLocation.withDefaultNamespace("hud/heart/" + name);
  }

  private static void render(GuiGraphics graphics) {
    Minecraft mc = Minecraft.getInstance();
    Player player = mc.player;

    if (player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
      return;
    }

    int lost = lostHearts(player);
    float maxHealth = player.getMaxHealth();
    int health = Mth.ceil(player.getHealth());
    int absorption = Mth.ceil(player.getAbsorptionAmount());

    int normalHearts = Mth.ceil(maxHealth / 2.0F);
    int absorbHearts = Mth.ceil(absorption / 2.0F);
    int total = normalHearts + lost + absorbHearts;

    long age = player.tickCount;

    if (health != lastHealth) {
      if (lastHealth != -1) {
        blinkEnd = age + 6;
      }

      lastHealth = health;
    }

    boolean blinking = blinkEnd > age;
    int rows = Mth.ceil(total / 10.0F);
    int rowHeight = Math.max(10 - (rows - 2), 3);
    int left = graphics.guiWidth() / 2 - 91;
    int bottom = graphics.guiHeight() - 39;
    String type = "";

    if (player.hasEffect(MobEffects.WITHER)) {
      type = "withered_";
    } else if (player.hasEffect(MobEffects.POISON)) {
      type = "poisoned_";
    } else if (player.isFullyFrozen()) {
      type = "frozen_";
    }

    int regenIndex = player.hasEffect(MobEffects.REGENERATION)
        ? (int) (age % Mth.ceil(maxHealth + 5.0F))
        : -1;
    Random rng = new Random(age * 312871L);

    ResourceLocation container = vanilla(blinking ? "container_hardcore_blinking" : "container_hardcore");

    for (int i = total - 1; i >= 0; i--) {
      int x = left + (i % 10) * 8;
      int y = bottom - (i / 10) * rowHeight;

      if (health + absorption <= 4) {
        y += rng.nextInt(2);
      }

      if (i == regenIndex) {
        y -= 2;
      }

      if (i < normalHearts) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, container, x, y, 9, 9);
        boolean full = health >= i * 2 + 2;
        boolean half = health == i * 2 + 1;

        if (full || half) {
          graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
              vanilla(type + "hardcore_" + (full ? "full" : "half") + (blinking ? "_blinking" : "")),
              x, y, 9, 9);
        }
      } else if (i < normalHearts + lost) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BROKEN_HEART, x, y, 9, 9);
      } else {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, container, x, y, 9, 9);
        int j = i - normalHearts - lost;
        boolean full = absorption >= j * 2 + 2;
        boolean half = absorption == j * 2 + 1;

        if (full || half) {
          graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
              vanilla("absorbing_hardcore_" + (full ? "full" : "half")), x, y, 9, 9);
        }
      }
    }
  }
}
