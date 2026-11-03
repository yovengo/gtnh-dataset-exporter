package dev.yovengo.gtnhdatasetexporter.client;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public final class ExportCommand extends CommandBase {

    private final ExportController controller;

    public ExportCommand(ExportController controller) {
        this.controller = controller;
    }

    @Override
    public String getCommandName() {
        return "gtnhexport";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtnhexport [cancel|inspect]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) controller.requestOpen();
        else if (args.length == 1 && args[0].equals("cancel")) controller.cancel();
        else if (args.length == 1 && args[0].equals("inspect")) controller.inspect(sender);
        else sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "cancel", "inspect") : null;
    }
}
