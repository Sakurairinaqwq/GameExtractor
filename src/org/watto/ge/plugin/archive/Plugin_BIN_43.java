/*
 * Application:  Game Extractor
 * Author:       wattostudios
 * Website:      http://www.watto.org
 * Copyright:    Copyright (c) 2002-2025 wattostudios
 *
 * License Information:
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License
 * published by the Free Software Foundation; either version 2 of the License, or (at your option) any later versions. This
 * program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranties
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License at http://www.gnu.org for more
 * details. For further information on this application, refer to the authors' website.
 */

package org.watto.ge.plugin.archive;

import java.io.File;
import java.util.HashMap;

import org.watto.Language;
import org.watto.Settings;
import org.watto.datatype.Archive;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_BIN_43 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_BIN_43() {

    super("BIN_43", "BIN_43");

    //         read write replace rename
    setProperties(true, false, true, false);

    setGames("Prince of Persia: Revelations");
    setExtensions("bin"); // MUST BE LOWER CASE
    setPlatforms("PSP");

    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    setTextPreviewExtensions("txs"); // LOWER CASE

    setCanScanForFileTypes(true);

  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public int getMatchRating(FileManipulator fm) {
    try {

      int rating = 0;

      if (FieldValidator.checkExtension(fm, extensions)) {
        rating += 25;
      }

      long arcSize = fm.getLength();

      // First File Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // Magic
      if (fm.readInt() == -285228903) {
        rating += 25;
      }

      return rating;

    }
    catch (Throwable t) {
      return 0;
    }
  }

  /**
   **********************************************************************************************
   * Reads an [archive] File into the Resources
   **********************************************************************************************
   **/
  @Override
  public Resource[] read(File path) {
    try {

      // NOTE - Compressed files MUST know their DECOMPRESSED LENGTH
      //      - Uncompressed files MUST know their LENGTH

      addFileTypes();

      //ExporterPlugin exporter = Exporter_ZLib.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      // Loop through directory
      int realNumFiles = 0;
      while (fm.getOffset() < arcSize) {

        // 4 - File Length (only the length of the File Data field)
        long length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - Magic Number
        fm.skip(4);

        // 4 - File ID
        int fileID = fm.readInt();
        //System.out.println(fileID);

        // X - File Data
        long offset = fm.getOffset();
        fm.skip(length);

        String filename = Resource.generateFilename(realNumFiles);

        //path,name,offset,length,decompLength,exporter
        Resource resource = new Resource(path, filename, offset, length);
        resource.addProperty("FileID", fileID);
        resources[realNumFiles] = resource;
        realNumFiles++;

        TaskProgressManager.setValue(offset);
      }

      resources = resizeResources(resources, realNumFiles);

      // go through and find any files that might be file lists
      fm.getBuffer().setBufferSize(2000);
      numFiles = realNumFiles;

      HashMap<Integer, String> extensions = new HashMap<Integer, String>(numFiles);

      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        int length = (int) resource.getLength();
        if (length < 2000 && length > 8 && length % 8 == 0) { // guess max length of file
          // maybe

          fm.seek(resource.getOffset());

          //System.out.println(resource.getName());

          int numEntries = length / 8;
          boolean extensionFile = true;
          String[] tempExtensions = new String[numEntries];
          int[] tempIDs = new int[numEntries];
          for (int e = 0; e < numEntries; e++) {
            // 4 - File ID
            int fileID = fm.readInt();

            // 4 - Extension
            String extension = fm.readString(4);

            if (extension.charAt(0) != '.') {
              // this file doesn't really contain an extensions list
              extensionFile = false;
              break;
            }

            tempIDs[e] = fileID;
            tempExtensions[e] = extension;

            //System.out.println(extension);

          }
          if (extensionFile) {
            // only add these extensions if the whole file was validly an extensions file
            for (int e = 0; e < numEntries; e++) {
              extensions.put(tempIDs[e], tempExtensions[e]);
            }
          }
        }
        else if (length < 2000 && length > 8 && length % 4 == 0) { // guess max length of file
          // maybe

          fm.seek(resource.getOffset());

          //System.out.println(resource.getName());

          int numEntries = (length - 4) / 8;
          boolean extensionFile = true;
          String[] tempExtensions = new String[numEntries];
          int[] tempIDs = new int[numEntries];
          for (int e = 0; e < numEntries; e++) {
            // 4 - File ID
            int fileID = fm.readInt();

            // 4 - Extension
            String extension = fm.readString(4);

            if (extension.charAt(0) != '.') {
              // this file doesn't really contain an extensions list
              extensionFile = false;
              break;
            }

            tempIDs[e] = fileID;
            tempExtensions[e] = extension;

            //System.out.println(extension);

          }
          if (extensionFile) {
            // only add these extensions if the whole file was validly an extensions file
            for (int e = 0; e < numEntries; e++) {
              extensions.put(tempIDs[e], tempExtensions[e]);
            }
          }
        }
      }

      // now we have all the known extensions - go set them on the files
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        int fileID = Integer.parseInt(resource.getProperty("FileID"));
        String extension = extensions.get(fileID);
        if (extension != null) {
          String filename = resource.getName() + extension;
          resource.setName(filename);
          resource.setOriginalName(filename);
        }
      }

      fm.close();

      return resources;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

  /**
   **********************************************************************************************
   * Writes an [archive] File with the contents of the Resources. The archive is written using
   * data from the initial archive - it isn't written from scratch.
   **********************************************************************************************
   **/
  @Override
  public void replace(Resource[] resources, File path) {
    try {

      FileManipulator fm = new FileManipulator(path, true);
      FileManipulator src = new FileManipulator(new File(Settings.getString("CurrentArchive")), false);

      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      // Write Directory and Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        long length = resource.getDecompressedLength();

        // 4 - File Length (only the length of the File Data field)
        int srcLength = src.readInt();
        fm.writeInt(length);

        // 4 - Magic Number (-285228903)
        // 4 - File ID
        fm.writeBytes(src.readBytes(8));

        // X - File Data
        write(resource, fm);
        src.skip(srcLength);

        TaskProgressManager.setValue(i);
      }

      src.close();
      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

  /**
  **********************************************************************************************
  If an archive doesn't have filenames stored in it, the scanner can come here to try to work out
  what kind of file a Resource is. This method allows the plugin to provide additional plugin-specific
  extensions, which will be tried before any standard extensions.
  @return null if no extension can be determined, or the extension if one can be found
  **********************************************************************************************
  **/
  @Override
  public String guessFileExtension(Resource resource, byte[] headerBytes, int headerInt1, int headerInt2, int headerInt3, short headerShort1, short headerShort2, short headerShort3, short headerShort4, short headerShort5, short headerShort6) {

    String currentExtension = resource.getExtension();
    if (currentExtension != null && !currentExtension.equals("")) {
      return currentExtension;
    }

    if (headerInt1 == 1868654382) {
      return "gao";
    }
    else if (headerInt1 == 2003793710) {
      return "wow";
    }
    else if (headerInt1 == 1 && headerInt2 == 7 && headerInt3 == 3) {
      return "mesh";
    }
    else if (headerInt1 == 4) {
      return "texture_pack";
    }
    else if (headerInt1 == 1) {
      return "geometry";
    }
    else if (headerInt1 == 5) {
      return "texture_info";
    }
    else if (headerInt1 == 267895006) {
      return "terminator";
    }
    else if (headerInt2 == 257 && headerInt3 == 16776960) {
      return "type19"; // length=19
    }

    long length = resource.getLength();

    if (headerInt2 == -1 && headerInt3 == 1074397187) {
      if (length < 68) {
        return "tex_header";
      }
      else {
        return "tex";
      }
    }
    else if ((headerBytes[3] >= 20 && headerBytes[3] < 30) && length == 2084) {
      return "pal";
    }
    else if ((headerInt1 * 88 + 4) == length) {
      return "ttt"; // Known extension. Contains a number of entries of length 88
    }

    else if (headerBytes[4] == 46 && (length % 8 == 0)) {
      return "ext_list"; // maybe a list of extensions
    }

    return null;
  }

}
