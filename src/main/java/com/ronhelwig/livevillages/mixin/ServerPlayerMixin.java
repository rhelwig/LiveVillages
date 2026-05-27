package com.ronhelwig.livevillages.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import com.ronhelwig.livevillages.sim.OutpostTrust;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Redirect(
		method = "startSleepInBed",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
		)
	)
	private <T extends Entity> List<T> livevillages$ignoreTrustedOutpostRaidersForSleep(
		ServerLevel level,
		Class<T> entityClass,
		AABB bounds,
		Predicate<? super T> predicate
	) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		return level.getEntitiesOfClass(entityClass, bounds, predicate).stream()
			.filter(entity -> !(entity instanceof Monster monster) || !OutpostTrust.shouldIgnoreSleepDanger(player, monster))
			.toList();
	}
}
