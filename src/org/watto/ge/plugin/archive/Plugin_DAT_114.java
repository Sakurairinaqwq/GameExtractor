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

import org.watto.datatype.Archive;
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.converter.StringConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_DAT_114 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_DAT_114() {

    super("DAT_114", "DAT_114");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Green Ranch");
    setExtensions("dat"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("img", "Image Archive", FileType.TYPE_ARCHIVE),
        new FileType("musc", "Music Archive", FileType.TYPE_ARCHIVE),
        new FileType("samp", "Sound Archive", FileType.TYPE_ARCHIVE));

    //setTextPreviewExtensions("colours", "rat", "screen", "styles"); // LOWER CASE

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

      fm.skip(8);

      if (fm.readInt() == 16) {
        rating += 5;
      }

      if (fm.readInt() == 1) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Directory Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
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

      // 8 - Unknown
      // 4 - Unknown (16)
      // 4 - Unknown (1)
      fm.skip(16);

      // 4 - Directory Offset
      int dirOffset = fm.readInt();
      FieldValidator.checkOffset(dirOffset, arcSize);

      fm.seek(dirOffset);

      // 4 - Number of Groups (can be null)
      int numGroups = fm.readInt();
      FieldValidator.checkNumFiles(numGroups + 1); // +1 to allow nulls

      // 4 - Number of File Types (can be null)
      int numTypes = fm.readInt();
      FieldValidator.checkNumFiles(numTypes + 1); // +1 to allow nulls

      // 4 - Hash?
      fm.skip(4);

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      int realNumFiles = 0;

      for (int g = 0; g < numGroups; g++) {
        // 4 - File Type (string, reversed)
        byte[] typeBytes = fm.readBytes(4);
        typeBytes = new byte[] { typeBytes[3], typeBytes[2], typeBytes[1], typeBytes[0] };
        if (typeBytes[3] == 0) {
          typeBytes = new byte[] { typeBytes[0], typeBytes[1], typeBytes[2] };
        }
        int typeLength = typeBytes.length;
        boolean ascii = true;
        for (int t = 0; t < typeLength; t++) {
          if (typeBytes[t] < 32) {
            ascii = false;
            break;
          }
        }
        String type = null;
        if (ascii) {
          type = "." + StringConverter.convertLittle(typeBytes);
        }
        else {
          type = "";
        }

        // 4 - Unknown
        // 4 - null
        fm.skip(8);

        // 4 - Number of Objects in this Group
        int numObjects = fm.readInt();
        FieldValidator.checkNumFiles(numObjects);

        // 4 - Hash?
        fm.skip(4);

        for (int i = 0; i < numObjects; i++) {
          // 4 - File Type (string, reversed)
          fm.skip(4);

          // 4 - Unknown
          fm.skip(4);
          // 4 - File Data Offset
          int offset = fm.readInt();
          FieldValidator.checkOffset(offset, arcSize);

          // 4 - File Data Length
          int length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          String filename = Resource.generateFilename(realNumFiles) + type;

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
          realNumFiles++;

          TaskProgressManager.setValue(offset);
        }
      }

      for (int i = 0; i < numTypes; i++) {

        // 4 - File Type (string, reversed)
        byte[] typeBytes = fm.readBytes(4);
        typeBytes = new byte[] { typeBytes[3], typeBytes[2], typeBytes[1], typeBytes[0] };
        if (typeBytes[3] == 0) {
          typeBytes = new byte[] { typeBytes[0], typeBytes[1], typeBytes[2] };
        }
        int typeLength = typeBytes.length;
        boolean ascii = true;
        for (int t = 0; t < typeLength; t++) {
          if (typeBytes[t] < 32) {
            ascii = false;
            break;
          }
        }
        String type = null;
        if (ascii) {
          type = "." + StringConverter.convertLittle(typeBytes);
        }
        else {
          type = "";
        }

        // 4 - Unknown
        fm.skip(4);

        // 4 - File Data Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Data Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        String filename = Resource.generateFilename(realNumFiles) + type;

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length);
        realNumFiles++;

        TaskProgressManager.setValue(offset);
      }

      resources = resizeResources(resources, realNumFiles);

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
