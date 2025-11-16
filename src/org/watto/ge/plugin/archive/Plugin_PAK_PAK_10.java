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

import org.watto.datatype.FileType;
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
public class Plugin_PAK_PAK_10 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_PAK_PAK_10() {

    super("PAK_PAK_10", "PAK_PAK_10");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Hot Farm Africa");
    setExtensions("pak"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("gfx", "GFX Image", FileType.TYPE_IMAGE));

    setTextPreviewExtensions("vcproj", "vcxproj", "filters", "h", "rc"); // LOWER CASE

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
      if (fm.readString(3).equals("PAK")) {
        rating += 50;
      }

      if (fm.readByte() == 1) {
        rating += 3;
      }
      if (fm.readByte() == 1) {
        rating += 2;
      }

      fm.skip(4);

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Directory Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
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

      long arcSize = fm.getLength();

      // 3 - Header (PAK)
      // 1 - Version Major? (1)
      // 1 - Version Minor? (1)
      // 4 - Unknown (5)
      fm.skip(9);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Filename Directory Length [*2]
      int filenameDirLength = fm.readInt() * 2;
      FieldValidator.checkLength(filenameDirLength, arcSize);

      byte[] nameBytes = fm.readBytes(filenameDirLength);
      FileManipulator nameFM = new FileManipulator(new ByteBuffer(nameBytes));

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      long dataOffset = fm.getOffset() + (numFiles * 48);

      // Loop through directory
      int realNumFiles = 0;
      String[] parentNames = new String[numFiles];
      for (int i = 0; i < numFiles; i++) {
        // 4 - Unknown
        fm.skip(4);

        // 4 - Filename Offset [*2] (relative to the start of the Filename Directory)
        int filenameOffset = fm.readInt() * 2;
        FieldValidator.checkOffset(filenameOffset, filenameDirLength);

        nameFM.seek(filenameOffset);
        String filename = nameFM.readNullUnicodeString();

        // 4 - Parent Directory ID (-1 for no parent)
        int parentID = fm.readInt();
        if (parentID != -1) {
          filename = parentNames[parentID] + filename;
        }

        // 4 - Unknown ID
        // 4 - Number of Files in this Folder? (0 = File Entry)
        fm.skip(8);

        // 8 - File Offset (relative to the start of the File Data) (0 = Folder Entry)
        long offset = fm.readLong();
        FieldValidator.checkOffset(offset, arcSize);

        // 8 - File Length (0 = Folder Entry)
        long length = fm.readLong();
        FieldValidator.checkLength(length, arcSize);

        // 8 - Unknown
        // 4 - Unknown
        fm.skip(12);

        if (offset != 0 && length != 0) {
          // a file
          offset += dataOffset;
        }
        else {
          // a folder
          parentNames[i] = filename + "\\";
          continue;
        }

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length);
        realNumFiles++;

        TaskProgressManager.setValue(i);
      }

      nameFM.close();

      fm.close();

      resources = resizeResources(resources, realNumFiles);

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
