package com.ronhelwig.livevillages.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.brigadier.tree.CommandNode;

@Mixin(CommandNode.class)
public interface CommandNodeAccessor<S> {
	@Accessor("requirement")
	@Mutable
	void livevillages$setRequirement(Predicate<S> requirement);
}
