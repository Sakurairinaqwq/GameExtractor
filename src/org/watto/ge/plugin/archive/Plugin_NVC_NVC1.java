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
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_NVC_NVC1 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_NVC_NVC1() {

    super("NVC_NVC1", "NVC_NVC1");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Jupiter Hell");
    setExtensions("nvc"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    setTextPreviewExtensions("vs", "ivs", "fs", "inc", "ifs", "csv"); // LOWER CASE

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
      if (fm.readString(4).equals("nvc1")) {
        rating += 50;
      }

      if (fm.readInt() == 100) {
        rating += 5;
      }

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

      Exporter_ZLib exporter = Exporter_ZLib.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 4 - Header (nvc1)
      // 4 - Version? (100)
      fm.skip(8);

      // 4 - Number Of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {
        // 8 - Hash?
        fm.skip(8);

        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - Decompressed File Length
        int decompLength = fm.readInt();
        FieldValidator.checkLength(decompLength);

        // 4 - Compressed File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - Compression Flag (1=compressed, 0=uncompressed)
        int compressionFlag = fm.readInt();

        String filename = Resource.generateFilename(i);

        if (compressionFlag == 0) {
          // uncompressed
          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, offset, length);
        }
        else if (compressionFlag == 1) {
          // ZLib Compression
          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, offset, length, decompLength, exporter);
        }
        else {
          ErrorLogger.log("[NVC_NVC1] Unknown compression type: " + compressionFlag);
          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, offset, length, decompLength);
        }

        TaskProgressManager.setValue(i);
      }

      // The last file contains the filenames, so lets extract it and read it
      Resource lastResource = resources[numFiles - 1];
      fm.seek(lastResource.getOffset());
      int compLength = (int) lastResource.getLength();
      int decompLength = (int) lastResource.getDecompressedLength();

      byte[] decompBytes = new byte[decompLength];

      exporter.open(fm, compLength, decompLength);

      int outPos = 0;
      while (exporter.available()) {
        decompBytes[outPos] = (byte) exporter.read();
        outPos++;
      }

      fm.close();

      // now read the filenames
      fm = new FileManipulator(new ByteBuffer(decompBytes));

      // 4 - Number of Files
      fm.skip(4);

      for (int i = 0; i < numFiles - 1; i++) { // -1 to skip the last file, which is the directory
        // 4 - Filename Length
        int filenameLength = fm.readInt();

        // X - Filename
        String filename = fm.readString(filenameLength);
        FieldValidator.checkFilename(filename);

        Resource resource = resources[i];
        resource.setName(filename);
        resource.setOriginalName(filename); // so it doesn't think it's been renamed
      }

      String lastName = "directory.lst";
      lastResource.setName(lastName);
      lastResource.setOriginalName(lastName);

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
  If an archive doesn't have filenames stored in it, the scanner can come here to try to work out
  what kind of file a Resource is. This method allows the plugin to provide additional plugin-specific
  extensions, which will be tried before any standard extensions.
  @return null if no extension can be determined, or the extension if one can be found
  **********************************************************************************************
  **/
  @Override
  public String guessFileExtension(Resource resource, byte[] headerBytes, int headerInt1, int headerInt2, int headerInt3, short headerShort1, short headerShort2, short headerShort3, short headerShort4, short headerShort5, short headerShort6) {

    /*
    if (headerInt1 == 2037149520) {
      return "js";
    }
    */

    return null;
  }

}
