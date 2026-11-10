package dev.irisejen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class IrisEjenMod implements ModInitializer {
    public static final String MOD_ID = "irisejen";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                IrisCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(IrisManager::tick);
    }
}
