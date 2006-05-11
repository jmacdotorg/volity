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
import org.volity.javolin.JavolinApp;
import org.volity.javolin.PlatformWrapper;

/**
 * Manages the on-disk cache of the Volity UI files.
 *
 * This maintains two cache directories. For each UI, UIFileCache contains the
 * UI file, exactly as downloaded. UIDirCache contains a directory containing
 * the unzipped contents of the UI file. (Or, if the UI file is not a ZIP
 * archive, simply another copy of it.)
 *
 * XXX: This is not completely safe against midgame UI changes. If you are
 * playing a game in one window, and you start a new game (with the same UI
 * URL) in a different window, and the UI file has changed -- in between the
 * two game starts -- then the first game will see the cache directory change
 * out from under it. This will, in general, crash things. Possible solution:
 * don't update the cache if there are any existing windows with the same UI
 * URL.
 */
public class UIFileCache
{
    private Set mDownloadedFiles; // URLs of files downloaded during current session
    private File mFileCacheDir; // Cache of downloaded UI files
    private File mDirCacheDir; // Cache of unzipped UI directories

    /**
     * Constructor.
     */
    public UIFileCache()
    {
        String cacheDirName = PlatformWrapper.getCacheDir();
        mFileCacheDir = new File(cacheDirName, "UIFileCache");
        mDirCacheDir = new File(cacheDirName, "UIDirCache");

        mDownloadedFiles = new HashSet();

        // Create the cache dirs
        ensureCacheDirsExist();
    }

    /**
     * Create the cache dir and its subdirs, if necessary. We check this
     * frequently, because some bozo might delete cache data while Javolin is
     * running.
     */
    protected void ensureCacheDirsExist() 
    {
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
     * Takes a UI URL, and returns a munged string which can serve as a single
     * component of a pathname in the UI cache. The plan is that two different
     * URLs will never produce the same munged string.
     *
     * The algorithm is to accept alphanumerics, "-", and "." as they stand.
     * All "/" are converted to "+". Anything else is converted to a "$HH" hex
     * sequence (or "$HHH" or "$HHHH" if the URL manages to contain Unicode
     * values beyond 255).
     *
     * So "http://volity.org/games/rps/svg/rps.svg" becomes
     * "http$3a++volity.org+games+rps+svg+rps.svg".
     *
     * @param fileLoc  A URL pointing at a Volity UI file.
     * @return         A munged string which contains no slashes and can be
     *                 used as a file or directory name.
     */
    private static String urlToCacheName(URL fileLoc)
    {
        StringBuffer res = new StringBuffer();

        char arr[] = fileLoc.toString().toCharArray();
        for (int ix=0; ix<arr.length; ix++) 
        {
            char ch = arr[ix];
            if ((ch >= 'a' && ch <= 'z')
                || (ch >= 'A' && ch <= 'Z')
                || (ch >= '0' && ch <= '9')
                || ch == '.' || ch == '-')
            {
                res.append(ch);
            }
            else if (ch == '/')
            {
                res.append('+');
            }
            else 
            {
                res.append('$');
                res.append(Integer.toHexString((int)ch));
            }
        }
        
        return res.toString();            
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
        // First, double-check that the cache dirs exist.
        ensureCacheDirsExist();

        // Figure out all the pathnames we'll be using.
        String name = urlToCacheName(uiURL);
        File cacheFile = new File(mFileCacheDir, name);
        File cacheDir = new File(mDirCacheDir, name+".d");

        boolean download = false;
        boolean unzip = false;

        // Determine whether the file needs to be downloaded.
        if (!cacheFile.exists())
        {
            download = true;
        }
        else
        {
            long ourmodtime = cacheFile.lastModified();

            URLConnection connection = uiURL.openConnection();
            connection.setUseCaches(false);
            if (connection instanceof HttpURLConnection) {
                HttpURLConnection hconn = (HttpURLConnection)connection;
                hconn.setRequestMethod("HEAD");
            }

            long srcmodtime = connection.getLastModified();

            if (srcmodtime > 0)
            {
                // Download if modified date of file at URL is greater
                // than that of the file on disk
                download = (srcmodtime > ourmodtime);
            }
            else
            {
                // Modified date of file at URL is unknown, so check
                // to see whether we've already downloaded that file
                // this session
                download = !mDownloadedFiles.contains(uiURL.toString());
            }
        }

        // If we've lost the dir cache, we'll need to unzip regardless.
        if (!cacheDir.exists()) {
            unzip = true;
        }

        /* Now the work begins. Download the file (if necessary), and then
         * unzip it (if necessary. */

        if (download)
        {
            copyFile(uiURL, cacheFile);
            // Remember that we've downloaded it
            mDownloadedFiles.add(uiURL.toString()); 

            /* Since we've downloaded a new copy of the file, the
             * cached unzipped version is invalid. */
            unzip = true;
        }

        if (unzip) {
            if (cacheDir.exists()) {
                boolean success = deleteRecursively(cacheDir);
                if (!success) 
                {
                    // Our cache is horked in some way. Sorry.
                    throw new IOException("unable to delete old UI data from cache");
                }
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
     * Clean out the UI cache. 
     *
     * If includeDirs is false, this only wipes the FileCache. Subsequent cache
     * requests will be certain to download and unpack new copies of the files,
     * but games in progress will not be disturbed.
     *
     * If includeDirs is true, this wipes both FileCache and DirCache. This
     * reduces cache disk usage to zero, but it will crash games in progress.
     * Do not set this flag if any TableWindows are open.
     */
    public void clearCache(boolean includeDirs)
    {
        deleteRecursively(mFileCacheDir);
        if (includeDirs)
            deleteRecursively(mDirCacheDir);
        ensureCacheDirsExist();
    }

    /** 
     * Clean one UI out of the UI cache.
     *
     * If includeDirs is false, this only wipes the FileCache. Subsequent cache
     * requests will be certain to download and unpack new copies of the files,
     * but games in progress will not be disturbed.
     *
     * If includeDirs is true, this wipes both FileCache and DirCache. This
     * will crash games in progress. Do not set this flag if any TableWindows
     * are open for the game in question.
     */
    public void clearCache(URL uiURL, boolean includeDirs)
    {
        ensureCacheDirsExist();

        // Figure out all the pathnames we'll be using.
        String name = urlToCacheName(uiURL);
        File cacheFile = new File(mFileCacheDir, name);
        File cacheDir = new File(mDirCacheDir, name+".d");

        if (cacheFile.exists())
            cacheFile.delete();

        if (includeDirs) {
            if (cacheDir.exists())
                deleteRecursively(cacheDir);
        }
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
                File destParent = destLoc.getParentFile();
                if (destParent != null && !destParent.exists())
                    destParent.mkdirs();

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

        /* Little trick here. We *don't* want to recursively follow symlinks to
         * directories -- we just want to delete the symlink. But a symlink to
         * a directory will test true in isDirectory(). So, we try a delete()
         * first. If that fails, and the file appears to be a directory, we
         * recurse. Finally, we delete() again to get rid of empty
         * directories. */
        if (name.delete())
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

    /**
     * Look in a directory to see if it contains anything interesting, or if
     * it's just a wrapper around a subdirectory (and nothing else). In the
     * latter case, look into the subdirectory, and so on recursively.
     *
     * The first directory which is "interesting" (contains any files, or
     * contains more than one subdirectory) is the final result. This may be
     * the same as the directory that was passed in to begin with.
     *
     * Files and directories beginning with "." are ignored in the search.
     * (This keeps MacOSX from confusing everybody with its ".DS_Store" cruft.)
     *
     * This function is useful to search a directory created by unpacking a ZIP
     * file (or other archive). Some people create archives with the important
     * files at the top level; others create archives with everything important
     * wrapped in a folder. This function handles both -- or, indeed, any
     * number of wrappers -- and gives you back the directory in which to find
     * things.
     *
     * @param dir  the directory in which to search
     * @return     the directory which contains significant files
     */
    public static File locateTopDirectory(File dir)
    {
        while (true)
        {
            File[] entries = dir.listFiles();

            // Locate the sole (non-dot) entry.
            int count = 0;
            File loneFile = null;
            for (int ix=0; ix<entries.length; ix++) {
                if (!entries[ix].isHidden()) {
                    count++;
                    loneFile = entries[ix];
                }
            }

            if (count != 1) {
                break;
            }
            if (!(loneFile.isDirectory())) {
                break;
            }
            dir = loneFile;
        }

        return dir;
    }

    /**
     * Look in a directory and locate the "main file". If there is only one
     * file (and no directories), that's it. Otherwise, look for "main.svg" or
     * "MAIN.SVG".
     *
     * The directory you pass to this function should already have been run
     * through locateTopDirectory().
     *
     * @param dir  the directory in which to search
     * @return     the file which is the main document.
     */
    public static File locateMainFile(File dir)
        throws IOException
    {
        File uiMainFile;
        File[] entries = dir.listFiles();

        if (entries.length == 1 && !entries[0].isDirectory())
        {
            uiMainFile = entries[0];
        }
        else
        {
            uiMainFile = findFileCaseless(dir, "main.svg");
            if (uiMainFile == null)
            {
                throw new IOException("unable to locate UI file in directory "
                    + dir);
            }
        }

        return uiMainFile;
    }

    /**
     * Given a directory and a string, locate a directory entry which matches
     * the string, case-insensitively. More precisely: this looks for an entry
     * which matches name, name.toLowerCase(), or name.toUpperCase(). It will
     * not find arbitrary mixed-case entries.
     *
     * @param dir   the directory to search.
     * @param name  the file/dir name to search for.
     * @return      a File representing an existing file/dir; or null, if no 
     *         entry was found.
     */
    public static File findFileCaseless(File dir, String name)
    {
        File res;
        String newname;

        res = new File(dir, name);
        if (res.exists())
        {
            return res;
        }

        newname = name.toUpperCase();
        if (!newname.equals(name))
        {
            res = new File(dir, newname);
            if (res.exists())
            {
                return res;
            }
        }

        newname = name.toLowerCase();
        if (!newname.equals(name))
        {
            res = new File(dir, newname);
            if (res.exists())
            {
                return res;
            }
        }

        return null;
    }

}
