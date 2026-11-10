package dev.irisejen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class IrisCommands {
    private IrisCommands() {}

    private static ServerPlayer pl(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("iris")
                .executes(c -> { IrisManager.status(pl(c)); return 1; });

        root.then(Commands.literal("scan")
                .executes(c -> { IrisScanner.scan(pl(c), true, true); return 1; })
                .then(Commands.literal("entities").executes(c -> { IrisScanner.scan(pl(c), true, false); return 1; }))
                .then(Commands.literal("ores").executes(c -> { IrisScanner.scan(pl(c), false, true); return 1; })));

        root.then(Commands.literal("override").executes(c -> { IrisManager.toggleOverride(pl(c)); return 1; }));
        root.then(Commands.literal("evade").executes(c -> { IrisManager.toggleEvade(pl(c)); return 1; }));
        root.then(Commands.literal("radar").executes(c -> { IrisManager.toggleRadar(pl(c)); return 1; }));
        root.then(Commands.literal("mark").executes(c -> { IrisManager.mark(pl(c)); return 1; }));
        root.then(Commands.literal("status").executes(c -> { IrisManager.status(pl(c)); return 1; }));
        root.then(Commands.literal("next").executes(c -> {
            ServerPlayer p = pl(c); IrisManager.select(p, IrisManager.get(p).selected.next()); return 1; }));
        root.then(Commands.literal("prev").executes(c -> {
            ServerPlayer p = pl(c); IrisManager.select(p, IrisManager.get(p).selected.prev()); return 1; }));
        root.then(Commands.literal("list").executes(c -> {
            ServerPlayer p = pl(c);
            for (Skill sk : Skill.values())
                IrisManager.msg(p, sk.label() + " (" + sk.cost + "%) - " + sk.desc, net.minecraft.ChatFormatting.GRAY);
            return 1; }));

        LiteralArgumentBuilder<CommandSourceStack> use = Commands.literal("use")
                .executes(c -> { ServerPlayer p = pl(c); IrisManager.useSkill(p, IrisManager.get(p).selected); return 1; });
        LiteralArgumentBuilder<CommandSourceStack> select = Commands.literal("select");
        for (Skill sk : Skill.values()) {
            use.then(Commands.literal(sk.id).executes(c -> { IrisManager.useSkill(pl(c), sk); return 1; }));
            select.then(Commands.literal(sk.id).executes(c -> { IrisManager.select(pl(c), sk); return 1; }));
        }
        root.then(use);
        root.then(select);
        d.register(root);
    }
}
