/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.jagrosh.jmusicbot;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.jagrosh.jdautilities.commandclient.Command.Category;
import me.jagrosh.jdautilities.commandclient.CommandEvent;
import me.jagrosh.jdautilities.waiter.EventWaiter;
import me.jagrosh.jmusicbot.audio.AudioHandler;
import me.jagrosh.jmusicbot.gui.GUI;
import me.jagrosh.jmusicbot.utils.FormatUtil;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ShutdownEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.utils.PermissionUtil;
import net.dv8tion.jda.core.utils.SimpleLog;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class Bot extends ListenerAdapter {
    
    private final HashMap<String,Settings> settings;
    private final AudioPlayerManager manager;
    private final EventWaiter waiter;
    private final ScheduledExecutorService threadpool;
    private JDA jda;
    private GUI gui;
    //private GuildsPanel panel;
    public final Category MUSIC = new Category("Music");
    public final Category DJ = new Category("DJ", event -> 
    {
        if(event.getAuthor().getId().equals(event.getClient().getOwnerId()))
            return true;
        if(event.getGuild()==null)
            return true;
        if(PermissionUtil.checkPermission(event.getGuild(), event.getMember(), Permission.MANAGE_SERVER))
            return true;
        Role dj = event.getGuild().getRoleById(getSettings(event.getGuild()).getRoleId());
        return event.getMember().getRoles().contains(dj);
    });
    public final Category ADMIN = new Category("Admin", event -> 
    {
        if(event.getAuthor().getId().equals(event.getClient().getOwnerId()))
            return true;
        if(event.getGuild()==null)
            return true;
        return PermissionUtil.checkPermission(event.getGuild(), event.getMember(), Permission.MANAGE_SERVER);
    });
    public final Category OWNER = new Category("Owner");
    
    public Bot(EventWaiter waiter)
    {
        this.waiter = waiter;
        this.settings = new HashMap<>();
        manager = new DefaultAudioPlayerManager();
        threadpool = Executors.newSingleThreadScheduledExecutor();
        AudioSourceManagers.registerRemoteSources(manager);
        try {
            JSONObject loadedSettings = new JSONObject(new String(Files.readAllBytes(Paths.get("serversettings.json"))));
            for(String id: loadedSettings.keySet())
            {
                JSONObject o = loadedSettings.getJSONObject(id);
                settings.put(id, new Settings(o.has("text_channel_id")?o.getString("text_channel_id"):null,
                                              o.has("voice_channel_id")?o.getString("voice_channel_id"):null,
                                              o.has("dj_role_id")?o.getString("dj_role_id"):null));
            }
        } catch(IOException | JSONException e) {
            SimpleLog.getLog("Settings").warn("Failed to load server settings: "+e);
        }
    }
    
    public EventWaiter getWaiter()
    {
        return waiter;
    }
    
    public AudioPlayerManager getAudioManager()
    {
        return manager;
    }
    
    public int queueTrack(CommandEvent event, AudioTrack track)
    {
        return setUpHandler(event).addTrack(track, event.getAuthor());
    }
    
    public AudioHandler setUpHandler(CommandEvent event)
    {
        AudioHandler handler;
        if(event.getGuild().getAudioManager().getSendingHandler()==null)
        {
            AudioPlayer player = manager.createPlayer();
            handler = new AudioHandler(player, event.getGuild());
            player.addListener(handler);
            event.getGuild().getAudioManager().setSendingHandler(handler);
            threadpool.scheduleWithFixedDelay(() -> updateTopic(event.getGuild(),handler), 0, 5, TimeUnit.SECONDS);
        }
        else
        {
            handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
        }
        return handler;
    }
    
    private void updateTopic(Guild guild, AudioHandler handler)
    {
        TextChannel tchan = guild.getTextChannelById(getSettings(guild).getTextId());
        if(tchan!=null && PermissionUtil.checkPermission(tchan, guild.getSelfMember(), Permission.MANAGE_CHANNEL))
        {
            String otherText;
            if(tchan.getTopic()==null || tchan.getTopic().isEmpty())
                otherText = "";
            else if(tchan.getTopic().contains("\u200B"))
                otherText = tchan.getTopic().substring(tchan.getTopic().indexOf("\u200B"));
            else
                otherText = "\u200B\n "+tchan.getTopic();
            String text = FormatUtil.formattedAudio(handler, guild.getJDA(), true)+otherText;
            if(!text.equals(tchan.getTopic()))
                tchan.getManager().setTopic(text).queue();
        }
    }

    public void shutdown(){
        manager.shutdown();
        threadpool.shutdownNow();
        jda.getGuilds().stream().forEach(g -> {
            g.getAudioManager().closeAudioConnection();
            AudioHandler ah = (AudioHandler)g.getAudioManager().getSendingHandler();
            if(ah!=null)
            {
                ah.getQueue().clear();
                ah.getPlayer().destroy();
                updateTopic(g, ah);
            }
        });
        jda.shutdown();
    }

    public void setGUI(GUI gui)
    {
        this.gui = gui;
    }
    
    @Override
    public void onShutdown(ShutdownEvent event) {
        if(gui!=null)
            gui.dispose();
    }

    @Override
    public void onReady(ReadyEvent event) {
        this.jda = event.getJDA();
        //if(panel!=null)
        //    panel.updateList(event.getJDA().getGuilds());
    }
    
    
    // settings
    
    public Settings getSettings(Guild guild)
    {
        return settings.getOrDefault(guild.getId(), Settings.DEFAULT_SETTINGS);
    }
    
    public void setTextChannel(TextChannel channel)
    {
        Settings s = settings.get(channel.getGuild().getId());
        if(s==null)
        {
            settings.put(channel.getGuild().getId(), new Settings(channel.getId(),null,null));
        }
        else
        {
            s.setTextId(channel.getId());
        }
        writeSettings();
    }
    
    public void setVoiceChannel(VoiceChannel channel)
    {
        Settings s = settings.get(channel.getGuild().getId());
        if(s==null)
        {
            settings.put(channel.getGuild().getId(), new Settings(null,channel.getId(),null));
        }
        else
        {
            s.setVoiceId(channel.getId());
        }
        writeSettings();
    }
    
    public void setRole(Role role)
    {
        Settings s = settings.get(role.getGuild().getId());
        if(s==null)
        {
            settings.put(role.getGuild().getId(), new Settings(null,null,role.getId()));
        }
        else
        {
            s.setRoleId(role.getId());
        }
        writeSettings();
    }
    
    public void clearTextChannel(Guild guild)
    {
        Settings s = getSettings(guild);
        if(s!=null)
        {
            if(s.getVoiceId()==null && s.getRoleId()==null)
                settings.remove(guild.getId());
            else
                s.setTextId(null);
            writeSettings();
        }
    }
    
    public void clearVoiceChannel(Guild guild)
    {
        Settings s = getSettings(guild);
        if(s!=null)
        {
            if(s.getTextId()==null && s.getRoleId()==null)
                settings.remove(guild.getId());
            else
                s.setVoiceId(null);
            writeSettings();
        }
    }
    
    public void clearRole(Guild guild)
    {
        Settings s = getSettings(guild);
        if(s!=null)
        {
            if(s.getVoiceId()==null && s.getTextId()==null)
                settings.remove(guild.getId());
            else
                s.setRoleId(null);
            writeSettings();
        }
    }
    
    private void writeSettings()
    {
        JSONObject obj = new JSONObject();
        settings.keySet().stream().forEach(key -> {
            JSONObject o = new JSONObject();
            Settings s = settings.get(key);
            if(s.getTextId()!=null)
                o.put("text_channel_id", s.getTextId());
            if(s.getVoiceId()!=null)
                o.put("voice_channel_id", s.getVoiceId());
            if(s.getRoleId()!=null)
                o.put("dj_role_id", s.getRoleId());
            obj.put(key, o);
        });
        try {
            Files.write(Paths.get("serversettings.json"), obj.toString(4).getBytes());
        } catch(IOException ex){
            SimpleLog.getLog("Settings").warn("Failed to write to file: "+ex);
        }
    }
    
    //gui stuff
    /*public void registerPanel(GuildsPanel panel)
    {
        this.panel = panel;
        threadpool.scheduleWithFixedDelay(() -> updatePanel(), 0, 5, TimeUnit.SECONDS);
    }
    
    public void updatePanel()
    {
        System.out.println("updating...");
        Guild guild = jda.getGuilds().get(panel.getIndex());
        panel.updatePanel((AudioHandler)guild.getAudioManager().getSendingHandler());
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        if(panel!=null)
            panel.updateList(event.getJDA().getGuilds());
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        if(panel!=null)
            panel.updateList(event.getJDA().getGuilds());
    }

    @Override
    public void onShutdown(ShutdownEvent event) {
        ((GUI)panel.getTopLevelAncestor()).dispose();
    }*/
    
}
