package com.ronhelwig.livevillages.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class ProfessionOverlayTextures {
	private ProfessionOverlayTextures() {
	}

	public static boolean exists(Identifier texture) {
		if (texture == null) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getResourceManager() == null) {
			return false;
		}

		return minecraft.getResourceManager().getResource(texture).isPresent();
	}
}
