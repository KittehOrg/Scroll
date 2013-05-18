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

public class Scroll extends JavaPlugin implements Listener {
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

    private enum ChatVisibility {
        ALL,
        SELF,
        NONE;
        private static Map<String, ChatVisibility> map = new HashMap<String, ChatVisibility>();

        static {
            for (final ChatVisibility chat : ChatVisibility.values()) {
                ChatVisibility.map.put(chat.name().toLowerCase(), chat);
            }
        }

        public static ChatVisibility match(String chat) {
            return ChatVisibility.map.get(chat.toLowerCase());
        }
    }

    private static final List<Character> SPACER = ImmutableList.of(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ');
    private final Map<String, List<Character>> queues = new HashMap<String, List<Character>>();
    private Scoreboard board;
    private ChatVisibility visibility;

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
        final List<Character> queue = this.queues.get(event.getPlayer().getName());
        final String message = event.getMessage();
        final List<Character> split = new LinkedList<Character>();
        for (final char c : message.toCharArray()) {
            split.add(c);
        }
        queue.addAll(split);
        queue.addAll(Scroll.SPACER);
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
        final Team team = this.board.getTeam(event.getPlayer().getName());
        if (team != null) {
            team.unregister();
        }
        final String name = event.getPlayer().getName();
        this.getServer().getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                if (Scroll.this.getServer().getPlayerExact(name) == null) {
                    Scroll.this.queues.remove(name);
                }
            }
        }, 10);
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
}