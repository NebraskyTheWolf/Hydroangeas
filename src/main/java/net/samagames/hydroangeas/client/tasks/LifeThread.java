package net.samagames.hydroangeas.client.tasks;

import net.samagames.hydroangeas.client.HydroangeasClient;
import net.samagames.hydroangeas.client.servers.MinecraftServerC;
import net.samagames.hydroangeas.common.protocol.intranet.HeartbeatPacket;
import net.samagames.hydroangeas.common.protocol.intranet.HelloFromClientPacket;
import net.samagames.hydroangeas.common.protocol.intranet.MinecraftServerSyncPacket;
import net.samagames.hydroangeas.utils.InstanceType;
import net.samagames.hydroangeas.utils.ModMessage;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

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
public class LifeThread
{
    private final static long TIMEOUT = 20 * 1000L;
    private final HydroangeasClient instance;
    private long lastHeartbeatFromServer;
    private boolean connected;

    public LifeThread(HydroangeasClient instance)
    {
        this.instance = instance;
        this.connected = false;
    }

    public void start()
    {
        try {
            sendData(true);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        instance.getScheduler().scheduleAtFixedRate(this::check, 2, 10, TimeUnit.SECONDS);
    }

    public void check()
    {
        instance.getConnectionManager().sendPacket(new HeartbeatPacket(instance.getClientUUID()));

        if (System.currentTimeMillis() - lastHeartbeatFromServer > TIMEOUT)
        {
            if (this.connected)
            {
                ModMessage.sendMessage(InstanceType.CLIENT, "[" + this.instance.getClientUUID() + "] Impossible de contacter le serveur Hydroangeas !");
            }
            this.instance.log(Level.SEVERE, "Can't tell the Hydroangeas Server! Maybe it's down?");
            this.connected = false;
        } else if (!connected)
        {
            ModMessage.sendMessage(InstanceType.CLIENT, "[" + this.instance.getClientUUID() + "] Retour à la normale !");

            this.instance.log(Level.INFO, "Hydroangeas Server has responded!");
            try {
                sendData(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            connected = true;
        }
    }

    public void sendData(boolean all) throws InterruptedException {
        instance.getLogger().info("Resync data...");
        instance.getConnectionManager().sendPacket(new HelloFromClientPacket(instance));
        if (all)
        {
            Thread.sleep(3);
            for (MinecraftServerC server : instance.getServerManager().getServers())
            {
                instance.getConnectionManager().sendPacket(new MinecraftServerSyncPacket(instance, server));
            }
        }
    }

    public void onServerHeartbeat(UUID uuid)
    {
        lastHeartbeatFromServer = System.currentTimeMillis();
    }

    public boolean isConnected()
    {
        return connected;
    }

}
