/*
 * UIFileCache.java
 *
 * Copyright 2004 Karl von Laudermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.volity.javolin.game;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Manages the on-disk cache of the Volity UI files.
 */
public class UIFileCache
{
    private static String sSep; // File separator character

    private Set mDownloadedFiles; // URLs of files downloaded during current session
    private File mCacheDir;

    static
    {
        sSep = System.getProperty("file.separator");
    }

    /**
     * Constructor.
     */
    public UIFileCache()
    {
        String cacheDirName =
            System.getProperty("user.home") + sSep + ".Javolin" + sSep + "UICache";

        mCacheDir = new File(cacheDirName);
        mDownloadedFiles = new HashSet();

        // Create the cache dir
        if (!mCacheDir.exists())
        {
            mCacheDir.mkdirs();
        }
    }

    /**
     * Takes a URL and returns the file name, including the full path to the cache
     * directory, to which the file at the URL should be downloaded. The File returned
     * does not necessarily reference a file that actually exists; the purpose of this
     * method is to simply map a URL to the proper file name and location in the cache
     * directory.
     *
     * @param fileLoc  A URL pointing at a Volity UI file.
     * @return         A file name with full path indicating the proper location to store
     * the UI file locally.
     */
    private String urlToCachePath(URL fileLoc)
    {
        String retVal;

        String parts[] = fileLoc.getPath().split("/");
        String fileName = parts[parts.length - 1];
        retVal = mCacheDir.getPath() + sSep + fileName;

        return retVal;
    }

    /**
     * Gets a File object corresponding to the file found at the specified URL. The
     * actual file at the URL is downloaded and stored in the cache on the local hard
     * drive, if it does not already exist there, and then a File object for that file
     * is returned.
     *
     * @param fileLoc          A URL specifying a Volity UI file.
     * @return                 A File object for the specified file.
     * @exception IOException  If a connection could not be made to the URL item.
     */
    public File getFile(URL fileLoc) throws IOException
    {
        File cacheFile = new File(urlToCachePath(fileLoc));

        boolean download = false;

        // Determine whether the file needs to be downloaded
        if (!cacheFile.exists())
        {
            download = true;
        }
        else
        {
            URLConnection connection = fileLoc.openConnection();

            if (connection.getLastModified() > 0)
            {
                // Download if modified date of file at URL is greater than that of the
                // file on disk
                download = (connection.getLastModified() > cacheFile.lastModified());
            }
            else
            {
                // Modified date of file at URL is unknown, so check to see whether we've
                // already downloaded that file this session
                download = !mDownloadedFiles.contains(fileLoc.toString());
            }
        }

        // Download the file
        if (download)
        {
            downloadFile(fileLoc);
            mDownloadedFiles.add(fileLoc.toString()); // Remember that we've downloaded it
        }

        return cacheFile;
    }

    /**
     * Downloads a file at the given URL into the on-disk cache.
     *
     * @param fileLoc          The URL indicating the file to download.
     * @exception IOException  If the file could not be downloaded.
     */
    private void downloadFile(URL fileLoc) throws IOException
    {
        BufferedInputStream in = new BufferedInputStream(fileLoc.openStream());

        File cacheFile = new File(urlToCachePath(fileLoc));
        BufferedOutputStream out =
            new BufferedOutputStream(new FileOutputStream(cacheFile));

        int b = in.read();

        while (b != -1)
        {
            out.write(b);
            b = in.read();
        }

        in.close();
        out.close();
    }
}
