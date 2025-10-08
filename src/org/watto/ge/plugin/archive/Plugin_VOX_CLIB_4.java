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

import org.watto.ErrorLogger;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;
import org.watto.io.converter.StringConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_VOX_CLIB_4 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_VOX_CLIB_4() {

    super("VOX_CLIB_4", "VOX_CLIB_4");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Blackwell Unbound");
    setExtensions("dat"); // MUST BE LOWER CASE
    setPlatforms("PC");

    //setFileTypes("","",
    //             "",""
    //             );

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
      if (fm.readString(4).equals("CLIB")) {
        rating += 50;
      }

      fm.skip(3);

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
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

      // 4 - Header (CLIB)
      // 3 - Unknown
      fm.skip(7);

      // 4 - Number Of External Files
      int numArchives = fm.readInt();
      FieldValidator.checkNumFiles(numArchives);

      // 20*numFiles - External Filenames 
      String[] archiveNames = new String[numArchives];
      for (int i = 0; i < numArchives; i++) {
        // 20 - Archive Name
        archiveNames[i] = fm.readNullString(20);
      }

      archiveNames[0] = path.getName();

      File[] archives = new File[numArchives];
      long[] archiveLengths = new long[numArchives];
      String basePath = path.getParent() + File.separatorChar;
      for (int i = 0; i < numArchives; i++) {
        File arcFile = new File(basePath + archiveNames[i]);
        if (arcFile.exists() && arcFile.isFile()) {
          archives[i] = arcFile;
          archiveLengths[i] = arcFile.length();
        }
        else {
          ErrorLogger.log("[VOX_CLIB_4] Missing archive file: " + archiveNames[i]);
        }
      }

      // 4 - Number Of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      String[] filenames = new String[numFiles];

      int[] subtractKey = new int[] { 77, 121, 1, 222, 4, 74, 105, 98, 122, 108, 101, 77, 121, 1, 222, 4, 74, 105, 98, 122, 108, 101, 77, 121, 1, 222, 4, 74, 105, 98, 122, 108, 101 };

      // Loop through filenames directory
      for (int i = 0; i < numFiles; i++) {
        // 25 - Filename [subtract 20 from each byte, except for null bytes] (null terminated)
        byte[] filenameBytes = fm.readBytes(25);

        int filenameLength = 25;
        for (int b = 0; b < 25; b++) {
          filenameBytes[b] = (byte) (ByteConverter.unsign(filenameBytes[b]) - subtractKey[b]);
          if (filenameBytes[b] == 0) {
            // found the end of the filename
            filenameLength = b;
            break;
          }
        }

        if (filenameLength <= 0) {
          filenames[i] = Resource.generateFilename(i);
        }
        else {
          byte[] oldFilenameBytes = filenameBytes;
          filenameBytes = new byte[filenameLength];
          System.arraycopy(oldFilenameBytes, 0, filenameBytes, 0, filenameLength);
          filenames[i] = StringConverter.convertLittle(filenameBytes);
        }

        TaskProgressManager.setValue(i);
      }

      // Loop through directory
      int[] offsets = new int[numFiles];
      for (int i = 0; i < numFiles; i++) {
        // 4 - File Offset
        int offset = fm.readInt();
        offsets[i] = offset;

        TaskProgressManager.setValue(i);
      }

      // Loop through directory
      int[] lengths = new int[numFiles];
      for (int i = 0; i < numFiles; i++) {
        // 4 - File Length
        int length = fm.readInt();
        lengths[i] = length;

        TaskProgressManager.setValue(i);
      }

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {
        // 1 - Archive Number
        int archiveNumber = ByteConverter.unsign(fm.readByte());
        FieldValidator.checkRange(archiveNumber, 0, numArchives);

        File arcFile = archives[archiveNumber];
        long arcFileSize = archiveLengths[archiveNumber];

        String filename = filenames[i];

        int offset = offsets[i];
        FieldValidator.checkOffset(offset, arcFileSize);

        int length = lengths[i];
        FieldValidator.checkLength(length, arcFileSize);

        //path,name,offset,length,decompLength,exporter
        Resource resource = new Resource(arcFile, filename, offset, length);
        resource.forceNotAdded(true);

        resources[i] = resource;

        TaskProgressManager.setValue(i);
      }

      fm.close();

      return resources;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

}
