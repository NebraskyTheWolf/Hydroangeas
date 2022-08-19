package net.samagames.hydroangeas.client.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.samagames.hydroangeas.Hydroangeas;
import net.samagames.hydroangeas.client.HydroangeasClient;
import net.samagames.hydroangeas.client.servers.MinecraftServerC;
import net.samagames.hydroangeas.utils.NetworkUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.rauschig.jarchivelib.FileType;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.GZIPInputStream;

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
public class ResourceManager
{
    private static final Gson GSON = new GsonBuilder().enableComplexMapKeySerialization().create();
    private final HydroangeasClient instance;
    private final CacheManager cacheManager;

    public ResourceManager(HydroangeasClient instance)
    {
        this.instance = instance;

        this.cacheManager = new CacheManager(instance);
    }

    public void downloadServer(MinecraftServerC server, File serverPath) throws IOException
    {
        String existURL = this.instance.getTemplatesDomain() + "servers/exist.php?game=" + server.getGame();

        if (NetworkUtils.convert(existURL))
        {
            File dest = new File(serverPath, server.getGame() + ".tar.gz");

            FileUtils.copyFile(cacheManager.getServerFiles(server.getGame()), dest);

            Archiver archiver = ArchiverFactory.createArchiver("tar", "gz");
            archiver.extract(dest, serverPath.getAbsoluteFile());

        } else {
            Hydroangeas.getLogger().warning("Server template don't exist!");
        }
    }

    public void downloadMap(MinecraftServerC server, File serverPath) throws IOException
    {
        String existURL = this.instance.getTemplatesDomain() + "maps/exist.php?game=" + server.getGame() + "&map=" + server.getMap().replaceAll(" ", "_");

        if (NetworkUtils.convert(existURL))
        {
            File dest = new File(serverPath, server.getGame() + "_" + server.getMap().replaceAll(" ", "_") + ".tar.gz");

            FileUtils.copyFile(cacheManager.getMapFiles(server.getGame(), server.getMap().replaceAll(" ", "_")), dest);

            Archiver archiver = ArchiverFactory.createArchiver("tar", "gz");
            archiver.extract(dest, serverPath.getAbsoluteFile());
        } else {
            Hydroangeas.getLogger().warning("Server map don't exist!");
        }
    }

    public void downloadDependencies(MinecraftServerC server, File serverPath) throws IOException
    {
        File dependenciesFile = new File(serverPath, "dependencies.json");

        InputStreamReader fileReader = null;
        try
        {
            fileReader = new InputStreamReader(Files.newInputStream(dependenciesFile.toPath()), StandardCharsets.UTF_8);
            List<ServerDependency> dependencies = GSON.fromJson(fileReader, new TypeToken<List<ServerDependency>>()
            {
            }.getType());
            for (ServerDependency dependency : dependencies)
            {
                this.downloadDependency(server, dependency, serverPath);
            }
        } finally
        {
            try
            {
                if (fileReader != null)
                    fileReader.close();
            } catch (IOException e)
            {

            }
        }
    }

    public void downloadDependency(MinecraftServerC server, ServerDependency dependency, File serverPath) throws IOException
    {
        String existURL = this.instance.getTemplatesDomain() + "dependencies/exist.php?name=" + dependency.getName() + "&version=" + dependency.getVersion() + "&ext=" + dependency.getExt();
        File pluginsPath = new File(serverPath, "plugins");

        if (!pluginsPath.exists())
            FileUtils.forceMkdir(pluginsPath);

        if (NetworkUtils.convert(existURL))
        {
            File dest;
            if (dependency.getType().equals("server") && !dependency.isExtractable())
            {
                dest = new File(serverPath, "spigot.jar");
                if (dest.exists())
                    FileUtils.deleteQuietly(dest);
            } else
            {
                dest = new File(pluginsPath, dependency.getName() + "-" + dependency.getVersion() + "." + dependency.getExt());
            }

            FileUtils.copyFile(cacheManager.getDebFiles(dependency), dest);

            if (dependency.isExtractable())
                ArchiverFactory.createArchiver(FileType.get(dest)).extract(dest, pluginsPath.getAbsoluteFile());
        } else {
            Hydroangeas.getLogger().warning("Servers' dependency '" + dependency.getName() + "' don't exist!");
        }
    }

    public void patchServer(MinecraftServerC server, File serverPath) throws Exception
    {
        FileOutputStream outputStream = null;

        try
        {
            // Generate API configuration
            File apiConfiguration = new File(serverPath, "plugins" + File.separator + "SamaGamesAPI" + File.separator + "config.yml");
            FileUtils.deleteQuietly(apiConfiguration);
            FileUtils.forceMkdir(apiConfiguration.getParentFile());
            apiConfiguration.createNewFile();
            outputStream = new FileOutputStream(apiConfiguration);
            outputStream.write(("bungeename: " + server.getServerName()).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            outputStream.close();

            // Generate data.yml
            File credentialsFile = new File(serverPath, "data.yml");
            FileUtils.deleteQuietly(credentialsFile);
            outputStream = new FileOutputStream(credentialsFile);
            outputStream.write(("redis-bungee-ip: " + Hydroangeas.getInstance().getConfiguration().redisIp).getBytes(StandardCharsets.UTF_8));
            outputStream.write(System.getProperty("line.separator").getBytes());
            outputStream.write(("redis-bungee-port: " + Hydroangeas.getInstance().getConfiguration().redisPort).getBytes(StandardCharsets.UTF_8));
            outputStream.write(System.getProperty("line.separator").getBytes());
            outputStream.write(("redis-bungee-password: " + Hydroangeas.getInstance().getConfiguration().redisPassword).getBytes(StandardCharsets.UTF_8));
            outputStream.write(System.getProperty("line.separator").getBytes());

            outputStream.write(("sql-url: " + Hydroangeas.getInstance().getConfiguration().sqlURL).getBytes(StandardCharsets.UTF_8));
            outputStream.write(System.getProperty("line.separator").getBytes());
            outputStream.write(("sql-user: " + Hydroangeas.getInstance().getConfiguration().sqlUser).getBytes(StandardCharsets.UTF_8));
            outputStream.write(System.getProperty("line.separator").getBytes());
            outputStream.write(("sql-pass: " + Hydroangeas.getInstance().getConfiguration().sqlPassword).getBytes(StandardCharsets.UTF_8));
            outputStream.write(System.getProperty("line.separator").getBytes());

            outputStream.write(("data-url: " + this.instance.getSimpleTemplatesDomain()).getBytes(StandardCharsets.UTF_8));
            outputStream.write(System.getProperty("line.separator").getBytes());
            outputStream.flush();
            outputStream.close();
        }catch (Exception e)
        {
            e.printStackTrace();
        }

        this.instance.getLinuxBridge().sed("%serverPort%", String.valueOf(server.getPort()), new File(serverPath, "server.properties").getAbsolutePath());
        this.instance.getLinuxBridge().sed("%serverIp%", instance.getAsClient().getIP(), new File(serverPath, "server.properties").getAbsolutePath());
        this.instance.getLinuxBridge().sed("%serverName%", server.getServerName(), new File(serverPath, "scripts.txt").getAbsolutePath());

       if (!server.isHub()) {
           JsonObject rootJson = new JsonObject();
           rootJson.addProperty("template-id", server.getTemplateID());
           rootJson.addProperty("map-name", server.getMap());
           rootJson.addProperty("min-slots", server.getMinSlot());
           rootJson.addProperty("max-slots", server.getMaxSlot());

           rootJson.add("options", server.getOptions());

           File gameFile = new File(serverPath, "game.json");
           if (!gameFile.createNewFile())
           {
               throw new Exception("Erreur creation game.json");
           }
           try (FileOutputStream fOut = new FileOutputStream(gameFile)) {
               fOut.write(new Gson().toJson(rootJson).getBytes(StandardCharsets.UTF_8));
               fOut.flush();
           }
       }
    }

    public CacheManager getCacheManager()
    {
        return cacheManager;
    }
}