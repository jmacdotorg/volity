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
import java.util.zip.*;

/**
 * Manages the on-disk cache of the Volity UI files.
 *
 * This maintains two cache directories. For each UI, UIFileCache
 * contains the UI file, exactly as downloaded. UIDirCache contains a
 * directory containing the unzipped contents of the UI file. (Or, if
 * the UI file is not a ZIP archive, simply another copy of it.)
 */
public class UIFileCache
{
    private static String sSep; // File separator character

    private Set mDownloadedFiles; // URLs of files downloaded during current session
    private File mFileCacheDir; // Cache of downloaded UI files
    private File mDirCacheDir; // Cache of unzipped UI directories

    static
    {
        sSep = System.getProperty("file.separator");
    }

    /**
     * Constructor.
     *
     * @param MacStyle    If true, the cache is located in 
     *                    ~/Library/Caches/Javolin rather 
     *                    than ~/.Javolin
     */
    public UIFileCache(boolean MacStyle)
    {
        String cacheDirName = System.getProperty("user.home");

        if (!MacStyle) 
        {
            cacheDirName += sSep + ".Javolin";
        }
        else 
        {
            cacheDirName += sSep + "Library" + sSep + "Caches" + sSep + "Javolin";
        }
        mFileCacheDir = new File(cacheDirName, "UIFileCache");
        mDirCacheDir = new File(cacheDirName, "UIDirCache");

        mDownloadedFiles = new HashSet();

        // Create the cache dirs
        if (!mFileCacheDir.exists())
        {
            mFileCacheDir.mkdirs();
        }
        if (!mDirCacheDir.exists())
        {
            mDirCacheDir.mkdirs();
        }
    }

    /**
     * Takes a UI URL and returned a munged string which can serve as
     * a single component of a pathname in the UI cache. The hope is
     * that two different URLs will never produce the same munged
     * string.
     *
     * The algorithm is to convert "/" to "_", and then delete any
     * character except for alphanumerics, period, plus, minus, and
     * underscore. (So "http://volity.org/games/rps/svg/rps.svg"
     * becomes "http__volity.org_games_rps_svg_rps.svg".) It is
     * obviously possible to contrive collisions, but hopefully they
     * won't occur in real life.
     *
     * @param fileLoc  A URL pointing at a Volity UI file.
     * @return         A munged string which contains no slashes and can 
     *                 be used as a directory name.
     */
    private String urlToCacheName(URL fileLoc)
    {
        String retVal;

        retVal = fileLoc.toString().replace('/', '_');
        retVal = retVal.replaceAll("[^a-zA-Z0-9.+_-]+", "");

        return retVal;
    }

    /**
     * Gets a File object corresponding to the package found at the
     * specified URL. The actual file at the URL is downloaded and
     * stored in the cache on the local hard drive, if it does not
     * already exist there. If the file is a .zip file, it is unpacked
     * into a cache directory. If not, it is copied into the cache
     * directory.
     *
     * The return value is a File representing the cache directory.
     * The caller will have to find the file within it.
     *
     * @param uiURL            A URL specifying a Volity UI file or archive.
     * @return                 A File object for the cache directory.
     * @exception IOException  If a connection could not be made to the URL 
     *                         item.
     * @exception ZipException If a ZIP file could not be unpacked.
     */
    public File getUIDir(URL uiURL) throws IOException, ZipException
    {
        String name = urlToCacheName(uiURL);
        File cacheFile = new File(mFileCacheDir, name);
        File cacheDir = new File(mDirCacheDir, name+".d");

        boolean download = false;

        // Determine whether the file needs to be downloaded
        if (!cacheFile.exists())
        {
            download = true;
        }
        else
        {
            URLConnection connection = uiURL.openConnection();

            if (connection.getLastModified() > 0)
            {
                // Download if modified date of file at URL is greater
                // than that of the file on disk
                download = (connection.getLastModified() > cacheFile.lastModified());
            }
            else
            {
                // Modified date of file at URL is unknown, so check
                // to see whether we've already downloaded that file
                // this session
                download = !mDownloadedFiles.contains(uiURL.toString());
            }
        }

        // Download the file, and unzip it (if necessary)

        if (download)
        {
            copyFile(uiURL, cacheFile);
            // Remember that we've downloaded it
            mDownloadedFiles.add(uiURL.toString()); 
            
            /* Since we've downloaded a new copy of the file, the
             * cached unzipped version is invalid. */
            boolean success = deleteRecursively(cacheDir);
            if (!success) 
            {
                // Our cache is horked in some way. Sorry.
                throw new IOException("unable to delete old UI data from cache");
            }

            cacheDir.mkdirs();

            /* Is this a .zip file? Yes, I'm testing the filename
             * suffix, as opposed to the MIME type. Sorry. I expect
             * MIME to be wrong more often. Plus, we might have gotten
             * the file from local disk. */
            boolean iszip = uiURL.toString().toLowerCase().endsWith(".zip");
            
            if (!iszip) 
            {
                /* It's a plain file (HTML or SVG). For the sake of
                 * consistency and cleanliness, we're going to copy
                 * the file to the cacheDir. */

                String file = uiURL.getFile();
                int pos = file.lastIndexOf('/');
                if (pos >= 0) 
                {
                    file = file.substring(pos+1);
                }
                if (file.length() == 0) 
                {
                    file = "index.html";
                }
                File cacheDirFile = new File(cacheDir, file);
                copyFile(cacheFile, cacheDirFile);
            }
            else 
            {
                /* It's a ZIP file. We will unpack it into the cacheDir. */
                unzipFile(cacheFile, cacheDir);
            }
        }

        return cacheDir;
    }

    /**
     * Downloads a file at the given URL into a local file.
     *
     * @param srcLoc           The URL indicating the file to download.
     * @param destLoc          Location to write file.
     * @exception IOException  If the file could not be downloaded.
     */
    private void copyFile(URL srcLoc, File destLoc) throws IOException
    {
        InputStream stream = srcLoc.openStream();
        BufferedInputStream in = new BufferedInputStream(stream);
        copyFile(in, destLoc);
        in.close();
        stream.close();
    }

    /**
     * Copies a file from one location to another.
     *
     * @param srcLoc           Location to read file.
     * @param destLoc          Location to write file.
     * @exception IOException  If the file could not be read.
     */
    private void copyFile(File srcLoc, File destLoc) throws IOException
    {
        InputStream stream = new FileInputStream(srcLoc);
        BufferedInputStream in = new BufferedInputStream(stream);
        copyFile(in, destLoc);
        in.close();
        stream.close();
    }

    /**
     * Copies data from a given stream into a local file.
     *
     * @param in               An input stream.
     * @param destLoc          Location to write file.
     * @exception IOException  If the stream could not be read.
     */
    private void copyFile(BufferedInputStream in, File destLoc) 
        throws IOException
    {
        OutputStream stream = new FileOutputStream(destLoc);
        BufferedOutputStream out = new BufferedOutputStream(stream);

        int b = in.read();

        while (b != -1)
        {
            out.write(b);
            b = in.read();
        }

        out.close();
        stream.close();
    }

    /**
     * Unzip a ZIP file into the specified directory. 
     *
     * Items in the ZIP archive are unpacked into the destDir. Nested
     * directories maintain their nested structure. If the entries
     * have absolute pathnames, they are unpacked as if destDir was the
     * root.
     *
     * (Actually, I think ZIP entries cannot have absolute pathnames.
     * But we're going to be careful.)
     *
     * @param zipFile File to unzip.
     * @param destDir Directory to unzip to. This must exist, and should
     *                be empty (unless you want to overwrite old files).
     */
    private void unzipFile(File zipFile, File destDir) 
        throws IOException, ZipException
    {
        ZipFile zip = new ZipFile(zipFile);

        for (Enumeration contents = zip.entries(); 
             contents.hasMoreElements(); ) 
        {
            ZipEntry entry = (ZipEntry)contents.nextElement();
            String name = entry.getName();
            File destLoc = new File(destDir, name);
            // Even if name was absolute, destLoc is relative to destDir.

            if (entry.isDirectory())
            {
                destLoc.mkdirs();
            }
            else {
                InputStream zipStream = zip.getInputStream(entry);
                BufferedInputStream in = new BufferedInputStream(zipStream);
                copyFile(in, destLoc);
                in.close();
                zipStream.close();
            }
        }

        zip.close();
    }

    /**
     * Delete a file or directory, and all files and directories
     * contained within it.
     *
     * @param name File or directory to delete.
     * @return     Whether the operation succeeded. (If the object never
     *             existed, that's success.)
     */
    private static boolean deleteRecursively(File name) 
    {
        if (!name.exists()) 
        {
            return true;
        }

        if (name.isDirectory()) 
        {
            String[] children = name.list();
            for (int i=0; i<children.length; i++) 
            {
                boolean success = deleteRecursively(
                    new File(name, children[i]));
                if (!success) 
                {
                    return false;
                }
            }
        }
        
        return name.delete();
    }
}
