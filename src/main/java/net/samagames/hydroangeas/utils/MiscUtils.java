package net.samagames.hydroangeas.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
public class MiscUtils
{
    public static File getJarFolder()
    {
        URL url;
        String extURL;

        try
        {
            url = MiscUtils.class.getProtectionDomain().getCodeSource().getLocation();
        } catch (SecurityException ex)
        {
            url = MiscUtils.class.getResource(MiscUtils.class.getSimpleName() + ".class");
        }

        extURL = url.toExternalForm();

        if (extURL.endsWith(".jar"))
            extURL = extURL.substring(0, extURL.lastIndexOf("/"));
        else
        {
            String suffix = "/" + (MiscUtils.class.getName()).replace(".", "/") + ".class";
            extURL = extURL.replace(suffix, "");

            if (extURL.startsWith("jar:") && extURL.endsWith(".jar!"))
                extURL = extURL.substring(4, extURL.lastIndexOf("/"));
        }

        try
        {
            url = new URL(extURL);
        } catch (MalformedURLException ignored)
        {
        }

        try
        {
            return new File(url.toURI());
        } catch (URISyntaxException ex)
        {
            return new File(url.getPath());
        }
    }

    public static String getApplicationDirectory()
    {
        String jarDir = null;

        try
        {
            CodeSource codeSource = MiscUtils.class.getProtectionDomain().getCodeSource();
            File jarFile = new File(URLDecoder.decode(codeSource.getLocation().toURI().getPath(), "UTF-8"));
            jarDir = jarFile.getParentFile().getPath();
        } catch (URISyntaxException | UnsupportedEncodingException ex)
        {
            ex.printStackTrace();
        }

        return jarDir + "/";
    }

    public static String getSHA1(File f) throws NoSuchAlgorithmException, IOException
    {
        MessageDigest sha1 = MessageDigest.getInstance("SHA1");

        try (FileInputStream fis = new FileInputStream(f))
        {
            byte[] data = new byte[1024];
            int read = 0;
            while ((read = fis.read(data)) != -1)
            {
                sha1.update(data, 0, read);
            }
        }
        byte[] hashBytes = sha1.digest();

        StringBuilder sb = new StringBuilder();
        for (byte hashByte : hashBytes)
        {
            sb.append(Integer.toString((hashByte & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public static byte[] stringToByte(String ip)
    {
        String[] split = ip.split(".");
        byte[] result = new byte[4];
        int i = 0;
        for (String number : split)
        {
            result[i] = Byte.valueOf(number);
            i++;
        }
        return result;
    }

    public static int calculServerWeight(String game, int maxSlot, boolean isCoupaing)
    {
        game = game.toLowerCase();
        int weight = 0;

        //GameType
        switch (game)
        {
            case "uhc":
                weight += 40;
                break;
            case "uhcrun":
                weight += 60;
                break;
            case "quake":
                weight += 20;
                break;
            case "uppervoid":
                weight += 25;
                break;
            case "herobattle":
                weight += 30;
                break;
            case "dimension":
                weight += 30;
                break;
            default:
                break;
        }

        //SlotNumber
        weight += maxSlot;

        //Is coupaing
        if (isCoupaing)
        {
            weight += 50;
        }

        return weight;
    }
}
