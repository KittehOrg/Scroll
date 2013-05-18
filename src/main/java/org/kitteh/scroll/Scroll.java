/*
 * Copyright 2013 Kitteh Development
 *
 * This file is part of Scroll.
 *
 * Scroll is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Scroll is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Scroll.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kitteh.scroll;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.google.common.collect.ImmutableList;

public final class Scroll extends JavaPlugin implements Listener {
    private class Bump implements Runnable {
        @Override
        public void run() {
            for (final Player player : Scroll.this.getServer().getOnlinePlayers()) {
                final String name = player.getName();
                final List<Character> queue = Scroll.this.queues.get(name);
                if (queue.size() > 0) {
                    final Team team = Scroll.this.board.getTeam(name);
                    team.setSuffix(team.getSuffix().substring(1) + queue.remove(0));
                }
            }
        }
    }

    private class QueueRemoval implements Runnable {
        private final String name;

        private QueueRemoval(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            if (Scroll.this.getServer().getPlayerExact(this.name) == null) {
                Scroll.this.queues.remove(this.name);
            }
        }
    }

    /**
     * Available chat-bar chat visibility settings
     */
    public enum ChatVisibility {
        /**
         * Chat goes to chat as normal
         */
        ALL,
        /**
         * Only the player speaking will see his/her chat
         */
        SELF,
        /**
         * Nobody sees chat
         */
        NONE;

        private static Map<String, ChatVisibility> map = new HashMap<String, ChatVisibility>();

        static {
            for (final ChatVisibility chat : ChatVisibility.values()) {
                ChatVisibility.map.put(chat.name().toLowerCase(), chat);
            }
        }

        /**
         * Matches a given String to a ChatVisibility
         * Takes any case, returns null if no match
         *
         * @param match the string to match
         * @return a matched ChatVisibility or null if no match
         */
        public static ChatVisibility match(String match) {
            return ChatVisibility.map.get(match.toLowerCase());
        }
    }

    private static final List<Character> SPACER = ImmutableList.of(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ');
    private final Map<String, List<Character>> queues = new HashMap<String, List<Character>>();
    private Scoreboard board;
    private ChatVisibility visibility;

    /**
     * Queues a message for the player to scroll
     *
     * @param player player who is speaking
     * @param message message to be said
     */
    public synchronized void addMessage(Player player, String message) {
        final List<Character> split = new LinkedList<Character>();
        for (final char c : message.toCharArray()) {
            split.add(c);
        }
        final List<Character> queue = this.queues.get(player.getName());
        queue.addAll(split);
        queue.addAll(Scroll.SPACER);
    }

    /**
     * Gets the currently set chat visibility
     *
     * @return current chat visibility setting
     */
    public ChatVisibility getChatVisibility() {
        return this.visibility;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (this.visibility != ChatVisibility.ALL) {
            final Iterator<Player> iterator = event.getRecipients().iterator();
            while (iterator.hasNext()) {
                final Player player = iterator.next();
                if ((this.visibility == ChatVisibility.SELF) && player.equals(event.getPlayer())) {
                    continue;
                }
                iterator.remove();
            }
        }
        this.addMessage(event.getPlayer(), event.getMessage());
    }

    @Override
    public void onDisable() {
        for (final Player player : this.getServer().getOnlinePlayers()) {
            this.unregister(player);
        }
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.reloadConfig();
        final String chat = this.getConfig().getString("chat.visibility", "none");
        this.visibility = ChatVisibility.match(chat);
        if (this.visibility == null) {
            this.visibility = ChatVisibility.NONE;
        }
        this.getServer().getPluginManager().registerEvents(this, this);
        this.board = this.getServer().getScoreboardManager().getMainScoreboard();
        for (final Player player : this.getServer().getOnlinePlayers()) {
            this.register(player);
        }
        this.getServer().getScheduler().runTaskTimer(this, new Bump(), 3, 3);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.register(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        final String name = event.getPlayer().getName();
        final Team team = this.board.getTeam(name);
        if (team != null) {
            team.unregister();
        }
        this.getServer().getScheduler().runTaskLater(this, new QueueRemoval(name), 10);
    }

    private void register(Player player) {
        Team team = this.board.getTeam(player.getName());
        if (team == null) {
            team = this.board.registerNewTeam(player.getName());
        }
        team.addPlayer(player);
        team.setSuffix("                ");
        this.queues.put(player.getName(), new LinkedList<Character>());
    }

    private void unregister(Player player) {
        final Team team = this.board.getTeam(player.getName());
        if (team != null) {
            team.unregister();
        }
    }
}