package com.softhardcore;

import com.mojang.brigadier.Command;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class SoftHardcore implements ModInitializer {
  public static final String MOD_ID = "soft_hardcore";
  public static final int MAX_LIVES = 10;
  public static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID,
      "soft_hardcore");

  public static final AttachmentType<Integer> DEATHS = AttachmentRegistry.create(
      ResourceLocation.fromNamespaceAndPath(MOD_ID, "deads"),
      builder -> builder
          .initializer(() -> 0)
          .persistent(Codec.INT)
          .copyOnDeath());

  private static MinecraftServer currentServer;
  private static Path pendingDeletion;

  @Override
  public void onInitialize() {
    ServerLifecycleEvents.SERVER_STARTED.register(server -> {
      currentServer = server;
      pendingDeletion = null;
    });

    ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
      if (pendingDeletion != null) {
        deleteWorldAsync(pendingDeletion);
        pendingDeletion = null;
      }
      currentServer = null;
    });

    // Cada muerte = un corazón menos para siempre.
    ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
      if (!(entity instanceof ServerPlayer player))
        return;

      int deaths = getDeaths(player) + 1;
      player.setAttached(DEATHS, deaths);
      int lives = MAX_LIVES - deaths;

      if (lives > 0) {
        player.sendSystemMessage(
            Component.literal("💔 Has perdido un corazón para siempre. Vidas restantes: " + lives)
                .withStyle(ChatFormatting.RED));
      } else if (currentServer != null) {
        currentServer.getPlayerList().broadcastSystemMessage(
            Component.literal("☠ " + player.getName().getString() + " ha agotado todas sus vidas.")
                .withStyle(ChatFormatting.DARK_RED),
            false);
        if (isSingleplayerWorld(currentServer)) {
          markWorldForDeletion(currentServer);
        }
      }
    });

    ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
      if (isEliminated(newPlayer)) {
        handleEliminated(newPlayer);
        return;
      }
      applyMaxHealth(newPlayer);
      if (!alive)
        newPlayer.setHealth(newPlayer.getMaxHealth());
    });

    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
      ServerPlayer player = handler.player;
      if (isEliminated(player)) {
        handleEliminated(player);
      } else {
        applyMaxHealth(player);
      }
    });

    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("vidas").executes(ctx -> {
          ServerPlayer player = ctx.getSource().getPlayerOrException();
          int lives = Math.max(0, MAX_LIVES - getDeaths(player));
          ctx.getSource().sendSuccess(() -> Component.literal("Te quedan " + lives + " de " + MAX_LIVES + " vidas.")
              .withStyle(ChatFormatting.GOLD), false);
          return Command.SINGLE_SUCCESS;
        })));
  }

  // ---------------------------------------------------------------- lógica

  public static int getDeaths(ServerPlayer player) {
    return player.getAttachedOrElse(DEATHS, 0);
  }

  public static boolean isEliminated(ServerPlayer player) {
    return getDeaths(player) >= MAX_LIVES;
  }

  /** Aplica -2 HP (1 corazón) por cada muerte acumulada. */
  public static void applyMaxHealth(ServerPlayer player) {
    int deaths = Math.min(getDeaths(player), MAX_LIVES - 1);
    AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
    if (attr == null)
      return;

    attr.removeModifier(MODIFIER_ID);
    if (deaths > 0) {
      attr.addPermanentModifier(new AttributeModifier(
          MODIFIER_ID, -2.0 * deaths, AttributeModifier.Operation.ADD_VALUE));
    }
    if (player.getHealth() > player.getMaxHealth()) {
      player.setHealth(player.getMaxHealth());
    }
  }

  private static boolean isSingleplayerWorld(MinecraftServer server) {
    return !server.isDedicatedServer() && server.getPlayerCount() <= 1;
  }

  private static void handleEliminated(ServerPlayer player) {
    MinecraftServer server = currentServer;
    if (server == null)
      return;

    if (isSingleplayerWorld(server)) {
      // Como Hardcore: el mundo se pierde. Se borra al cerrarse el servidor.
      markWorldForDeletion(server);
      server.halt(false);
    } else {
      // Multijugador: no se borra el mundo, el jugador queda como espectador para
      // siempre.
      player.setGameMode(GameType.SPECTATOR);
      player.sendSystemMessage(
          Component.literal("☠ Has agotado todas tus vidas. Ahora solo puedes observar.")
              .withStyle(ChatFormatting.DARK_RED));
    }
  }

  private static void markWorldForDeletion(MinecraftServer server) {
    Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    // Seguridad: solo se borra si realmente es una carpeta de mundo.
    if (Files.exists(root.resolve("level.dat"))) {
      pendingDeletion = root;
    }
  }

  private static void deleteWorldAsync(Path root) {
    Thread thread = new Thread(() -> {
      for (int i = 0; i < 60; i++) {
        if (!Files.exists(root))
          return;
        try (Stream<Path> walk = Files.walk(root)) {
          walk.sorted(Comparator.reverseOrder()).forEach(p -> {
            try {
              Files.delete(p);
            } catch (IOException ignored) {
            }
          });
        } catch (IOException ignored) {
        }
        if (!Files.exists(root))
          return;
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          return;
        }
      }
    }, "corazonesrotos-borrar-mundo");
    thread.start();
  }
}