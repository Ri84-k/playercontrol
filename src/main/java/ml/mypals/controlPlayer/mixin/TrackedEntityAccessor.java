package ml.mypals.controlPlayer.mixin;


import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(ChunkMap.TrackedEntity.class)
public interface TrackedEntityAccessor {

	@Accessor
	Set<ServerPlayerConnection> getSeenBy();
	@Invoker
	void invokeRemovePlayer(ServerPlayer player);
	@Invoker
	void invokeUpdatePlayer(ServerPlayer player);
}