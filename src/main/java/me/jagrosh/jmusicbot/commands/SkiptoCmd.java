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
package me.jagrosh.jmusicbot.commands;

import me.jagrosh.jdautilities.commandclient.CommandEvent;
import me.jagrosh.jmusicbot.Bot;
import me.jagrosh.jmusicbot.audio.AudioHandler;
import net.dv8tion.jda.core.entities.User;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SkiptoCmd extends MusicCommand {

    public SkiptoCmd(Bot bot)
    {
        super(bot);
        this.name = "skipto";
        this.help = "skips to the current song";
        this.arguments = "<position>";
        this.aliases = new String[]{"jumpto"};
        this.bePlaying = true;
        this.category = bot.DJ;
    }

    @Override
    public void doCommand(CommandEvent event) {
        int index = 0;
        try
        {
            index = Integer.parseInt(event.getArgs());
        }
        catch(NumberFormatException e)
        {
            event.reply(event.getClient().getError()+" `"+event.getArgs()+"` is not a valid integer!");
            return;
        }
        AudioHandler handler = (AudioHandler)event.getGuild().getAudioManager().getSendingHandler();
        if(index<1 || index>handler.getQueue().size())
        {
            event.reply(event.getClient().getError()+" Position must be a valid integer between 1 and "+handler.getQueue().size()+"!");
            return;
        }
        handler.getQueue().skip(index-1);
        event.reply(event.getClient().getSuccess()+" Skipped to **"+handler.getQueue().get(0).getTrack().getInfo().title+"**");
        handler.getPlayer().stopTrack();
    }
    
}
