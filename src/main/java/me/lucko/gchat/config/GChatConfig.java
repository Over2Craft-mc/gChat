/*
 * This file is part of gChat, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.gchat.config;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.ToString;
import me.lucko.gchat.api.ChatFormat;
import net.kyori.text.Component;
import net.kyori.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.config.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@ToString
public class GChatConfig {
    public static String getStringNonNull(Configuration configuration, String path) throws IllegalArgumentException {
        String ret = configuration.getString(path);
        if (ret == null) {
            throw new IllegalArgumentException("Missing string value at '" + path + "'");
        }

        return ret;
    }

    private final boolean passthrough;
    private final boolean sendBungeeMessageOnlyOnDifferentServer;

    private final boolean requireSendPermission;
    private final Component requireSendPermissionFailMessage;
    private final boolean requireReceivePermission;
    private final boolean requirePermissionPassthrough;

    private final String logFile;
    private final boolean logChat;
    private final boolean logChatGlobal;

    private final List<ChatFormat> formats;

    public GChatConfig(Configuration c) {
        this.passthrough = c.getBoolean("passthrough", true);
        this.sendBungeeMessageOnlyOnDifferentServer = c.getBoolean("sendBungeeMessageOnlyOnDifferentServer", true);

        this.logFile = c.getString("log-file", "gChat.log");
        this.logChat = c.getBoolean("log-chat", true);
        this.logChatGlobal = c.getBoolean("log-chat-global", true);

        Configuration requirePermission = c.getSection("require-permission");
        if (requirePermission == null) {
            throw new IllegalArgumentException("Missing section: require-permission");
        }

        this.requireSendPermission = requirePermission.getBoolean("send", false);

        String failMsg = getStringNonNull(requirePermission, "send-fail");
        this.requireSendPermissionFailMessage = failMsg.isEmpty() ? null : LegacyComponentSerializer.legacy().deserialize(failMsg, '&');

        this.requireReceivePermission = requirePermission.getBoolean("receive", false);
        this.requirePermissionPassthrough = requirePermission.getBoolean("passthrough", true);

        Configuration formatsSection = c.getSection("formats");
        if (formatsSection == null) {
            throw new IllegalArgumentException("Missing section: formats");
        }

        Map<String, ChatFormat> formats = new HashMap<>();
        for (String id : formatsSection.getKeys()) {
            Configuration formatSection = formatsSection.getSection(id);
            if (formatSection == null) {
                continue;
            }

            formats.put(id.toLowerCase(), new ChatFormat(id.toLowerCase(), formatSection));
        }

        List<ChatFormat> formatsList = new ArrayList<>(formats.values());
        formatsList.sort((o1, o2) -> {
            int ret = Integer.compare(o1.getPriority(), o2.getPriority());
            return ret > 0 ? -1 : 1;
        });

        this.formats = ImmutableList.copyOf(formatsList);
    }

}
