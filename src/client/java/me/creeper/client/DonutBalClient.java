package me.creeper.client;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.Component;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class DonutBalClient implements ClientModInitializer {
	public static String apiToken = "";
	public static boolean hasApiToken = false;
	public Path CACHE_FILE = FabricLoader.getInstance().getConfigDir().resolve("donutbal-apicache.secret");
	public static volatile Map<String, PlayerBal> balances = new HashMap<>();
	private static int ticks = 0;

	@Override
	public void onInitializeClient() {
		load();

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			String raw = message.getString();

			if (!raw.startsWith("Your API Token is: ")) return;

			apiToken = raw.substring(19);
			hasApiToken = true;

			save(apiToken);
		});

		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (hasApiToken) {
				if (entity instanceof OtherClientPlayerEntity player) {
					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
						balances.put(player.getGameProfile().name(), new PlayerBal(getPlayerBal(player.getGameProfile().name()), System.currentTimeMillis()));
					});
				}
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world == null) return;
			ticks++;

			if (ticks >= 20 * 10) {
				ticks = 0;

				for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
					if (player == client.player) continue;

					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
						balances.put(player.getGameProfile().name(), new PlayerBal(getPlayerBal(player.getGameProfile().name()), System.currentTimeMillis()));
					});

				}
			}
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("donutbal", "before_chat"), DonutBalClient::render);
	}

	private void save(String data) {
		try {
			Files.createDirectories(CACHE_FILE.getParent());
			Files.writeString(CACHE_FILE, data);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void load() {
		try {
			if (!Files.exists(CACHE_FILE)) save("");
			apiToken = Files.readString(CACHE_FILE);
			if (!apiToken.isEmpty()) hasApiToken = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		if (!hasApiToken) {
			context.drawText(MinecraftClient.getInstance().textRenderer, "Run /api", 5, MinecraftClient.getInstance().getWindow().getScaledHeight()/2/2, 0xFFFFFFFF, true);
		}

		context.drawText(MinecraftClient.getInstance().textRenderer, "T: " + Thread.activeCount(), 40, MinecraftClient.getInstance().getWindow().getScaledWidth()/2/2, 0xFFFFFFFF, true);
	}

	private static final long T = 1_000_000_000_000L;
	private static final long B = 1_000_000_000L;
	private static final long M = 1_000_000;
	private static final long K = 1_000;
	private static final DecimalFormat DF = new DecimalFormat("0.##");
	static {
		DF.setRoundingMode(RoundingMode.HALF_UP);
	}
	public static String formatBalance(long bal) {
		if (bal >= T) {
			return formatUnit(bal, T) + "T";
		}
		if (bal >= B) {
			return formatUnit(bal, B) + "B";
		}
		if (bal >= M) {
			return formatUnit(bal, M) + "M";
		}
		if (bal >= K) {
			return formatUnit(bal, K) + "K";
		}

		return String.valueOf(bal);
	}
	private static String formatUnit(long val, long unit) {
		return DF.format((double)val / unit);
	}

	public static final AtomicLong lastCall = new AtomicLong(0);
	private static final long MIN_DELAY = 500;
	public static long getPlayerBal(String username) {
		while (true) {
			long now = System.currentTimeMillis();
			long prev = lastCall.get();

			long waitTime = MIN_DELAY - (now - prev);
			if (waitTime <= 0) {
				if (lastCall.compareAndSet(prev, now)) {
					break;
				}
			} else {
				try {
					Thread.sleep(Math.min(waitTime, 50));
				} catch (Exception e) {
					e.printStackTrace();
					return 0;
				}
			}
		}

		if (!hasApiToken) { return 0; }
        try (HttpClient client = HttpClient.newHttpClient()) {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://api.donutsmp.net/v1/stats/"+username))
					.header("Accept", "application/json")
					.header("Authorization", "Bearer " + apiToken)
					.GET()
					.build();


			try {
				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() != 200) {
					return 0;
				}

				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

				JsonObject result = root.getAsJsonObject("result");
				String moneyString = result.get("money").getAsString();
				if (moneyString.isEmpty()) { return 0; }
				return Math.round(Double.parseDouble(moneyString));
			} catch (Exception e) {
				e.printStackTrace();
                return 0;
            }
        }
	}
}