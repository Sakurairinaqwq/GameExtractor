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

import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_AR_ARES extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_AR_ARES() {

    super("AR_ARES", "AR_ARES");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Midtown Madness");
    setExtensions("ar");
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    setTextPreviewExtensions("cinfo", "info", "aimap", "aimap_p", "aivehicledata", "aivehiclemanager", "asbirthrule", "asflycs", "assimulation", "bat", "c", "c13", "c14", "c19", "c24", "c25", "c26", "c27", "c28", "c29", "c30", "c31", "cells", "gizmo", "mmbangerdata", "mmbridgemgr", "mmbridgeset", "mmcarsim", "mmdashview", "mminput", "mmnetworkcaraudio", "mmopponentcaraudio", "mmplayer", "mmplayercaraudio", "mmtrailer", "mod", "povcamcs", "rays", "skel", "trackcamcs", "tsh"); // LOWER CASE

    //setCanScanForFileTypes(true);

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

      // Header
      if (fm.readString(4).equals("ARES")) {
        rating += 50;
      }

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      fm.skip(4);

      // 4 - Filename Directory Length
      if (FieldValidator.checkLength(fm.readInt(), fm.getLength()))
        ;

      return rating;

    }
    catch (Throwable t) {
      return 0;
    }
  }

  int realNumFiles = 0;

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public Resource[] read(File path) {
    try {

      // RESET GLOBALS
      realNumFiles = 0;

      addFileTypes();

      FileManipulator fm = new FileManipulator(path, false);
      long arcSize = fm.getLength();

      // 4 - Header (ARES)
      fm.skip(4);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Number of Folders in the Root
      int numRootFolders = fm.readInt();
      FieldValidator.checkNumFiles(numRootFolders);

      // 4 - Filename Directory Length
      int filenameDirLength = fm.readInt();
      FieldValidator.checkLength(filenameDirLength, arcSize);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Get the filenames directory
      fm.skip(numFiles * 12);
      byte[] filenameBytes = fm.readBytes(filenameDirLength);
      fm.seek(16);

      FileManipulator nameFM = new FileManipulator(new ByteBuffer(filenameBytes));

      // Loop through directory
      readDirectory(fm, path, arcSize, resources, numRootFolders, "", nameFM);

      resources = resizeResources(resources, realNumFiles);

      nameFM.close();
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
  
  **********************************************************************************************
  **/

  public void readDirectory(FileManipulator fm, File path, long arcSize, Resource[] resources, int numEntries, String dirName, FileManipulator nameFM) {
    try {

      int[] dirOffsets = new int[numEntries];
      int[] dirFileCounts = new int[numEntries];
      String[] dirNames = new String[numEntries];
      int numDirs = 0;

      for (int i = 0; i < numEntries; i++) {
        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length [& 0x7FFFFF]
        int lengthField = fm.readInt();
        int length = (lengthField & 0x7FFFFF);
        FieldValidator.checkLength(length, arcSize);

        // 4 - Flags
        int flags = fm.readInt();

        boolean isDirectory = ((flags & 1) == 1);
        int filenameOffset = ((flags >> 14) & 0x3FFFF);

        nameFM.seek(filenameOffset);
        String name = nameFM.readNullString();

        int filenameNumber = ((flags >> 1) & 0x1FFF);
        //if (filenameNumber != 0) {
        int nameLength = name.length();
        for (int c = 0; c < nameLength; c++) {
          if (((int) name.charAt(c)) == 1) {
            name = name.substring(0, c) + filenameNumber + name.substring(c + 1);
          }
        }
        //}

        if (isDirectory) {
          // directory
          dirOffsets[numDirs] = 16 + (offset * 12);
          dirFileCounts[numDirs] = length;
          dirNames[numDirs] = dirName + name + "\\";
          numDirs++;
        }
        else {
          // file

          int extensionOffset = ((lengthField >> 23) & 0x1FF);
          nameFM.seek(extensionOffset);
          String extension = nameFM.readNullString();

          //System.out.println(offset + "\t" + length + "\t" + isDirectory + "\t" + extensionOffset + "\t" + filenameOffset + "\t" + filenameNumber);

          String filename = dirName + name + "." + extension;

          //path,id,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
          realNumFiles++;

          TaskProgressManager.setValue(realNumFiles);
        }
      }

      // now go through and process each directory
      for (int i = 0; i < numDirs; i++) {
        fm.seek(dirOffsets[i]);
        readDirectory(fm, path, arcSize, resources, dirFileCounts[i], dirNames[i], nameFM);
      }

    }
    catch (Throwable t) {
      logError(t);
      return;
    }
  }

}
