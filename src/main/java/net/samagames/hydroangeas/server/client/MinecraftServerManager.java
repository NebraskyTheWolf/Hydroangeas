package net.samagames.hydroangeas.server.client;

import net.samagames.hydroangeas.Hydroangeas;
import net.samagames.hydroangeas.common.protocol.intranet.AskForClientActionPacket;
import net.samagames.hydroangeas.common.protocol.intranet.MinecraftServerSyncPacket;
import net.samagames.hydroangeas.server.HydroangeasServer;
import net.samagames.hydroangeas.server.games.AbstractGameTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/*
 * This file is part of Hydroangeas.
 *
 * Hydroangeas is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hydroangeas is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hydroangeas.  If not, see <http://www.gnu.org/licenses/>.
 */
public class MinecraftServerManager
{

    private final HydroangeasServer instance;

    private final HydroClient client;
    private final List<MinecraftServerS> servers = new ArrayList<>();

    public MinecraftServerManager(HydroangeasServer instance, HydroClient client)
    {
        this.instance = instance;
        this.client = client;
    }


    public MinecraftServerS addServer(AbstractGameTemplate template, boolean hub)
    {
        MinecraftServerS server = new MinecraftServerS(client, template);

        if (!hub)
        {
            //Comme on prend que la première partie de l'uuid on check si un serveur a déja un nom identique
            while (instance.getClientManager().getServerByName(server.getServerName()) != null)
            {
                server.changeUUID();
            }
        } else
        {
            for (int i = 1; ; i++)
            {
                if (instance.getClientManager().getServerByName("Hub_" + i) == null)
                {
                    server.setHubID(i);
                    break;
                }
            }
        }

        server.setWeight(template.getWeight());

        servers.add(server);
        MinecraftServerSyncPacket packet = new MinecraftServerSyncPacket(server);
        packet.setTimeToLive(server.getTimeToLive());
        packet.setStartedTime(server.getStartedTime());

        packet.setAsker(server.getOwner());

        instance.getConnectionManager().sendPacket(client, packet);

        return server;
    }

    public void handleServerData(MinecraftServerSyncPacket packet)
    {
        if(packet.getServerName() == null)
            return;

        MinecraftServerS server = getServerByName(packet.getServerName());
        //Server not in here so add it
        if (server == null)
        {
            Hydroangeas.getLogger().severe("Error sync! server: " + packet.getServerName() + " not know by Hydroserver!");

            server = new MinecraftServerS(client, packet);

            if (getServerByUUID(server.getUUID()) != null || getServerByName(server.getServerName()) != null)
            {
                Hydroangeas.getLogger().severe("Error duplicated UUID ! Not saving server and ask Shutdown !");
                Hydroangeas.getLogger().severe("For information, Server Name: " + server.getServerName());
                instance.getConnectionManager().sendPacket(client,
                        new AskForClientActionPacket(instance.getUUID(), AskForClientActionPacket.ActionCommand.SERVEREND, packet.getServerName()));
                return;
            }

            server.setWeight(packet.getWeight());
            server.setTimeToLive(packet.getTimeToLive());
            server.setStartedTime(packet.getStartedTime());
            server.setTemplateID(packet.getTemplateID());
            server.setHubID(packet.getHubID());

            servers.add(server);
            Hydroangeas.getLogger().info("Added " + packet.getServerName());

            if(server.isHub())
            {
                instance.getHubBalancer().addStartedHub(server);
            }
        } else
        {//Server here ! so update it !

            //First check correspondance between uuid and serverName
            if (!server.getUUID().equals(packet.getMinecraftUUID()))
            {
                Hydroangeas.getLogger().severe("Error server: " + server.getServerName() + " has not the same UUID");
                instance.getConnectionManager().sendPacket(client,
                        new AskForClientActionPacket(instance.getUUID(), AskForClientActionPacket.ActionCommand.SERVEREND, packet.getServerName()));
                return;
            }
            server.setPort(packet.getPort());
        }
    }

    public void removeServer(String serverName)
    {
        MinecraftServerS server = getServerByName(serverName);
        if (server == null)
            return;

        server.onShutdown();
        servers.remove(server);
    }

    public MinecraftServerS getServerByName(String serverName)
    {
        List<MinecraftServerS> serverSes = new ArrayList<>(servers);
        for (MinecraftServerS server : serverSes)
            if (server.getServerName().equals(serverName))
                return server;
        return null;
    }

    public MinecraftServerS getServerByUUID(UUID uuid)
    {
        for (MinecraftServerS server : servers)
        {
            if (server.getUUID().equals(uuid))
            {
                return server;
            }
        }
        return null;
    }

    public List<MinecraftServerS> getServersByTemplate(AbstractGameTemplate template)
    {
        return servers.stream().filter(server -> server.getTemplateID() != null && server.getTemplateID().equalsIgnoreCase(template.getId())).collect(Collectors.toList());
    }

    public int getTotalWeight()
    {
        int weight = 0;
        for (MinecraftServerS serverS : servers)
        {
            weight += serverS.getWeight();
        }
        return weight;
    }

    public List<MinecraftServerS> getServers()
    {
        return servers;
    }


}
