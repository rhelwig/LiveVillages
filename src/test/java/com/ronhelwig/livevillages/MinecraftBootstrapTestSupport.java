package com.ronhelwig.livevillages;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

public abstract class MinecraftBootstrapTestSupport {
	@BeforeAll
	protected static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Bootstrap.validate();
	}
}
