package net.samagames.hydroangeas.server.waitingqueue;

import net.samagames.hydroangeas.common.packets.AbstractPacket;
import net.samagames.hydroangeas.common.protocol.queues.*;
import net.samagames.hydroangeas.server.HydroangeasServer;
import net.samagames.hydroangeas.server.games.AbstractGameTemplate;
import net.samagames.hydroangeas.utils.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
public class QueueManager
{

    public List<Queue> queues = new ArrayList<>();
    private final HydroangeasServer instance;

    public QueueManager(HydroangeasServer instance)
    {
        this.instance = instance;
    }

    public void handlePacket(QueueAddPlayerPacket packet)
    {

        QPlayer player = packet.getPlayer();
        Queue designedQueue = getQueue(packet);
        Queue currentQueue = getQueueByLeader(player.getUUID());

        if (designedQueue == null)
        {
            //No queue to add get out !
            //PlayerMessager.sendMessage(player.getUUID(), ChatColor.RED + "Aucun template disponible pour ce jeu!");
            QueueInfosUpdatePacket queueInfosUpdatePacket = new QueueInfosUpdatePacket(player, QueueInfosUpdatePacket.Type.REMOVE, packet.getTemplateID());

            queueInfosUpdatePacket.setGame(packet.getGame());
            queueInfosUpdatePacket.setMap(packet.getMap());

            queueInfosUpdatePacket.setErrorMessage(ChatColor.RED + "No template available for this game!");
            queueInfosUpdatePacket.setSuccess(false);

            sendPacketHub(queueInfosUpdatePacket);
            return;
        }

        if (designedQueue.equals(currentQueue))
        {
            //PlayerMessager.sendMessage(player.getUUID(), ChatColor.GREEN + "Tu es déjà dans la queue!");
            currentQueue.removeQPlayer(player);

            //PlayerMessager.sendMessage(player.getUUID(), ChatColor.YELLOW + "Vous quittez la queue " + currentQueue.getName());
            sendPacketHub(new QueueInfosUpdatePacket(player, QueueInfosUpdatePacket.Type.REMOVE, currentQueue.getGame(), currentQueue.getMap()));
            return;
        }

        if (currentQueue != null)
        {
            currentQueue.removeQPlayer(player);
        }

        ArrayList<QPlayer> players = new ArrayList<>();
        players.add(player);
        addPlayerToQueue(designedQueue, player, players);
    }

    public void handlePacket(QueueRemovePlayerPacket packet)
    {
        QPlayer player = packet.getPlayer();
        Queue currentQueue = getQueueByPlayer(player.getUUID());

        if (currentQueue == null)
        {
            //No queue, security remove ?
            QueueInfosUpdatePacket queueInfosUpdatePacket = new QueueInfosUpdatePacket(player, QueueInfosUpdatePacket.Type.REMOVE, packet.getTemplateID());
            queueInfosUpdatePacket.setGame(packet.getGame());
            queueInfosUpdatePacket.setMap(packet.getMap());

            queueInfosUpdatePacket.setErrorMessage("You have no queue!");

            sendPacketHub(queueInfosUpdatePacket);
            return;
        }

        currentQueue.removeQPlayer(player);
        QueueInfosUpdatePacket queueInfosUpdatePacket = new QueueInfosUpdatePacket(player, QueueInfosUpdatePacket.Type.REMOVE, currentQueue.getGame(), currentQueue.getMap());
        queueInfosUpdatePacket.setTemplateId(packet.getTemplateID());
        sendPacketHub(queueInfosUpdatePacket);
    }

    public void handlePacket(QueueAttachPlayerPacket packet)
    {
        QPlayer leader = packet.getLeader();

        for(QPlayer player : packet.getPlayers())
        {
            Queue currentQueue = getQueueByPlayer(player.getUUID());
            if(currentQueue != null && !player.getUUID().equals(leader.getUUID()))
            {
                currentQueue.removeQPlayer(player);
            }
        }

        Queue designedQueue = getQueueByLeader(leader.getUUID());

        if (designedQueue == null)
        {
            /*for(QPlayer qPlayer : packet.getPlayers())
            {
                PlayerMessager.sendMessage(qPlayer.getUUID(), ChatColor.RED + "Le leader de votre party n'a pas choisit de queue !");
            }*/
            return;
        }

        QGroup group = designedQueue.getGroupByLeader(leader.getUUID());

        if (group == null)
        {
            //Logically not possible but.. #MOJANG
            QueueInfosUpdatePacket queueInfosUpdatePacket = new QueueInfosUpdatePacket(null, QueueInfosUpdatePacket.Type.REMOVE, packet.getGame(), packet.getMap());
            queueInfosUpdatePacket.setTemplateId(packet.getTemplateID());
            queueInfosUpdatePacket.setSuccess(false);
            queueInfosUpdatePacket.setErrorMessage("You're not the leader of this group!");

            for (QPlayer qPlayer : packet.getPlayers())
            {
                queueInfosUpdatePacket.setPlayer(qPlayer);
                sendPacketHub(queueInfosUpdatePacket);
            }
            return;
        }
        designedQueue.removeGroup(group);
        packet.getPlayers().forEach(group::addPlayer);
        designedQueue.addGroup(group);

        //Inform group
        for (QPlayer qPlayer : packet.getPlayers())
        {
            if(!qPlayer.getUUID().equals(leader.getUUID()))
            {
                sendPacketHub(new QueueInfosUpdatePacket(qPlayer, QueueInfosUpdatePacket.Type.ADD, designedQueue.getGame(), designedQueue.getMap()));
            }
        }
    }

    public void handlePacket(QueueDetachPlayerPacket packet)
    {
        for (QPlayer player : packet.getPlayers())
        {
            Queue designedQueue = getQueueByPlayer(player.getUUID());
            if (designedQueue != null)
            {
                designedQueue.removeQPlayer(player);

                sendPacketHub(new QueueInfosUpdatePacket(player, QueueInfosUpdatePacket.Type.REMOVE, designedQueue.getGame(), designedQueue.getMap()));
                //PlayerMessager.sendMessage(player.getUUID(), "Vous avez été retiré de la queue " + designedQueue.getMap());
            }
        }
    }

    public Queue getQueue(QueuePacket packet)
    {
        Queue queue = null;
        QueuePacket.TypeQueue typeQueue = packet.getTypeQueue();

        try
        {
            if (typeQueue.equals(QueuePacket.TypeQueue.NAMED))
            {
                queue = instance.getQueueManager().getQueueByName(packet.getGame() + "_" + packet.getMap());
            } else if (typeQueue.equals(QueuePacket.TypeQueue.NAMEDID))
            {
                queue = instance.getQueueManager().getQueueByName(packet.getTemplateID());
            } else if (typeQueue.equals(QueuePacket.TypeQueue.FAST))
            {
                //TODO select best queue
            } else
            {
                //RANDOM
                List<Queue> queuesByGame = instance.getQueueManager().getQueuesByGame(packet.getGame());
                queue = queuesByGame.get(new Random().nextInt(queuesByGame.size()));
            }

            /*if(queue == null)
            {
                //error no queue
                //PlayerMessager.sendMessage(, ChatColor.RED + "Aucun template disponible pour ce jeu!");
                return null;
            }*/
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        return queue;
    }

    public void addPlayerToQueue(Queue queue, QPlayer leader, List<QPlayer> players)
    {
        queue.addPlayersInNewGroup(leader, players);

        for (QPlayer qPlayer : players)
        {
            sendPacketHub(new QueueInfosUpdatePacket(qPlayer, QueueInfosUpdatePacket.Type.ADD, queue.getGame(), queue.getMap()));
            //PlayerMessager.sendMessage(qPlayer.getUUID(), ChatColor.GREEN + "Vous avez été ajouté à la queue " + ChatColor.RED + queue.getMap());
        }
    }

    public void removePlayerFromQueue(Queue queue, QPlayer leader, List<QPlayer> players)
    {
        //TODO just do it !!!!!
    }

    public Queue addQueue(AbstractGameTemplate template)
    {
        Queue queue = new Queue(this, template);
        queues.add(queue);
        return queue;
    }

    public boolean removeQueue(String name)
    {
        Queue queue = getQueueByName(name);
        if (queue == null)
        {
            return false;
        }
        return removeQueue(queue);
    }

    public boolean removeQueue(Queue queue)
    {
        queue.remove();
        return queues.remove(queue);
    }

    public void disable()
    {
        queues.forEach(net.samagames.hydroangeas.server.waitingqueue.Queue::remove);
    }

    public Queue getQueueByName(String name)
    {
        for (Queue queue : queues)
        {
            if (queue.getName().equalsIgnoreCase(name))
            {
                return queue;
            }
        }
        return null;
    }

    public List<Queue> getQueuesByGame(String game)
    {
        return queues.stream().filter(queue -> queue.getGame().equalsIgnoreCase(game)).collect(Collectors.toList());
    }

    public Queue getQueueByTemplate(String templateID)
    {
        for (Queue queue : queues)
        {
            if (queue.getTemplate().getId().equalsIgnoreCase(templateID))
            {
                return queue;
            }
        }
        return null;
    }

    public Queue getQueueByPlayer(UUID player)
    {
        for (Queue queue : queues)
        {
            if (queue.containsUUID(player))
            {
                return queue;
            }
        }
        return null;
    }

    public Queue getQueueByLeader(UUID player)
    {
        for (Queue queue : queues)
        {
            if (queue.containsLeader(player))
            {
                return queue;
            }
        }
        return null;
    }

    public void sendPacketHub(AbstractPacket packet)
    {
        instance.getConnectionManager().sendPacket("hydroHubReceiver", packet);
    }

    public List<Queue> getQueues()
    {
        return queues;
    }

}
