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

package me.lucko.gchat;

import com.google.common.collect.Iterables;
import lombok.RequiredArgsConstructor;
import me.lucko.gchat.api.ChatFormat;
import me.lucko.gchat.api.events.GChatEvent;
import me.lucko.gchat.api.events.GChatMessageFormedEvent;
import me.lucko.gchat.api.events.GChatMessageSendEvent;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.adapter.bungeecord.TextAdapter;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class GChatListener implements Listener {
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)(" + String.valueOf('§') + "|&)[0-9A-FK-OR]");
    private final GChatPlugin plugin;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent e) {

        if (e.isCommand()) return;
        if (!(e.getSender() instanceof ProxiedPlayer)) return;

        ProxiedPlayer player = ((ProxiedPlayer) e.getSender());

        GChatEvent gChatEvent = new GChatEvent(player, e);
        plugin.getProxy().getPluginManager().callEvent(gChatEvent);

        if (gChatEvent.isCancelled()) {
            return;
        }

        // are permissions required to send chat messages?
        // does the player have perms to send the message
        if (plugin.getConfig().isRequireSendPermission() && !player.hasPermission("gchat.send")) {

            // if the message should be passed through when the player doesn't have the permission
            if (plugin.getConfig().isRequirePermissionPassthrough()) {
                // just return. the default behaviour is for the message to be passed to the backend.
                return;
            }

            // they don't have permission, and the message shouldn't be passed to the backend.
            e.setCancelled(true);

            Component failMessage = plugin.getConfig().getRequireSendPermissionFailMessage();
            if (failMessage != null) {
                TextAdapter.sendComponent(player, failMessage);
            }

            return;
        }

        ChatFormat format = plugin.getFormat(player).orElse(null);

        // couldn't find a format for the player
        if (format == null) {
            if (!plugin.getConfig().isPassthroughIfNoPerm()) {
                e.setCancelled(true);
            }

            return;
        }

        // we have a format, so cancel the event.
        //e.setCancelled(true);

        // get the actual message format, and apply replacements.
        String formatText = format.getFormatText();
        formatText = plugin.replacePlaceholders(player, formatText);

        // get any hover text, and apply replacements.
        String hover = format.getHoverText();
        hover = plugin.replacePlaceholders(player, hover);

        // get the click event type, and the value if present.
        ClickEvent.Action clickType = format.getClickType();
        String clickValue = format.getClickValue();
        if (clickType != null) {
            clickValue = plugin.replacePlaceholders(player, clickValue);
        }

        // get the players message, and remove any color if they don't have permission for it.
        String playerMessage;
        if (player.hasPermission("gchat.color")) {
            playerMessage = e.getMessage();
        } else {
            playerMessage = STRIP_COLOR_PATTERN.matcher(e.getMessage()).replaceAll("");
        }

        // apply the players message to the chat format
        formatText = formatText.replace("{message}", playerMessage);

        // apply any hover events
        HoverEvent hoverEvent = hover == null ? null : HoverEvent.showText(LegacyComponentSerializer.legacy().deserialize(hover, '&'));
        ClickEvent clickEvent = clickType == null ? null : ClickEvent.of(clickType, clickValue);

        // convert the format to a message
        TextComponent message = LegacyComponentSerializer.legacy().deserialize(formatText, '&').toBuilder()
                .applyDeep(m -> {
                    if (hoverEvent != null) {
                        m.hoverEvent(hoverEvent);
                    }
                    if (clickEvent != null) {
                        m.clickEvent(clickEvent);
                    }
                })
                .build();

        GChatMessageFormedEvent formedEvent = new GChatMessageFormedEvent(player, format, playerMessage, message);
        plugin.getProxy().getPluginManager().callEvent(formedEvent);

        // send the message to online players
        Iterable<ProxiedPlayer> recipients = Iterables.filter(plugin.getProxy().getPlayers(), p -> {

            if (p.getServer().getInfo().getName().equals(((ProxiedPlayer) e.getSender()).getServer().getInfo().getName())
                    && plugin.getConfig().isSendBungeeMessageOnlyOnDifferentServer()) {

                // Si regex match gChatSendMessageOnlyOn
                // we send gchat message
                Pattern patterngChatSendMessageOnlyOn = Pattern.compile(plugin.getConfig().getGChatSendMessageOnlyOn());
                Matcher gChatSendMessageOnlyOn = patterngChatSendMessageOnlyOn.matcher(p.getServer().getInfo().getName());
                Pattern patternpassthroughToBackend = Pattern.compile(plugin.getConfig().getPassthroughToBackend());
                Matcher passthroughToBackend = patternpassthroughToBackend.matcher(p.getServer().getInfo().getName());

                if (gChatSendMessageOnlyOn.find() && !passthroughToBackend.find()) {
                    System.out.println(1);
                    boolean cancelled = plugin.getConfig().isRequireReceivePermission() && !player.hasPermission("gchat.receive");
                    GChatMessageSendEvent sendEvent = new GChatMessageSendEvent(player, p, format, playerMessage, cancelled);
                    plugin.getProxy().getPluginManager().callEvent(sendEvent);
                    e.setCancelled(true);
                    return true;
                }

                if (!gChatSendMessageOnlyOn.find() && passthroughToBackend.find()) {
                    return false;
                }

                if (gChatSendMessageOnlyOn.find() && passthroughToBackend.find()) {
                    System.out.println(3);
                    boolean cancelled = plugin.getConfig().isRequireReceivePermission() && !player.hasPermission("gchat.receive");
                    GChatMessageSendEvent sendEvent = new GChatMessageSendEvent(player, p, format, playerMessage, cancelled);
                    plugin.getProxy().getPluginManager().callEvent(sendEvent);
                    return true;
                }
            }

            boolean cancelled = plugin.getConfig().isRequireReceivePermission() && !player.hasPermission("gchat.receive");
            GChatMessageSendEvent sendEvent = new GChatMessageSendEvent(player, p, format, playerMessage, cancelled);
            plugin.getProxy().getPluginManager().callEvent(sendEvent);
            return !sendEvent.isCancelled();
        });

        TextAdapter.sendComponent(recipients, message);
    }

}
