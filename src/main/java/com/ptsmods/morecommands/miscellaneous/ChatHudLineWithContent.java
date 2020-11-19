package com.ptsmods.morecommands.miscellaneous;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.StringRenderable;
import net.minecraft.util.Formatting;

public class ChatHudLineWithContent extends ChatHudLine {

    private String content = null;
    private String contentStripped = null;

    public ChatHudLineWithContent(int creationTick, StringRenderable stringRenderable, int id, String content) {
        super(creationTick, stringRenderable, id);
        setContent(content);
    }

    public void setContent(String content) {
        this.content = content;
        contentStripped = Formatting.strip(content).toLowerCase();
    }

    public String getContent() {
        return content;
    }

    public String getContentStripped() {
        return contentStripped;
    }

}
