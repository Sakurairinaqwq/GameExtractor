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
import org.watto.datatype.Archive;
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.FilenameSplitter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_SGP2 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_SGP2() {

    super("SGP2", "SGP2");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Fantastic 4", "Sopranos: Road to Respect");
    setExtensions("sgp2", "sgpx"); // MUST BE LOWER CASE
    setPlatforms("PS2", "PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("tex", "Texture Image", FileType.TYPE_IMAGE));

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

      if (fm.readLong() == 0) {
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

      FileManipulator fm = new FileManipulator(path, false, 128); // small quick reads

      long arcSize = fm.getLength();

      // 128 - Unknown
      fm.skip(128);

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      // Loop through directory
      int realNumFiles = 0;
      while (fm.getOffset() < arcSize) {
        long offset = fm.getOffset();

        //System.out.println(offset);

        TaskProgressManager.setValue(offset);

        // 4 - Type? (101)
        int type = fm.readShort();
        fm.skip(2);

        if (type == 101) {

          // 4 - Data Length (including all these headers)
          int length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          //  4 - Filename Offset (relative to the start of this file data) (44)
          long filenameOffset = offset + fm.readInt();
          FieldValidator.checkOffset(filenameOffset, arcSize);

          // X - Other stuff (similar to the TEX2 archives)
          fm.relativeSeek(filenameOffset);

          // 84 - Filename (null terminated, filled with nulls)
          String filename = fm.readNullString();
          FieldValidator.checkFilename(filename);

          String extension = FilenameSplitter.getExtension(filename);
          if (extension.toLowerCase().equals("tga")) {
            filename += ".tex";
          }

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
          realNumFiles++;

          fm.seek(offset + length);
        }
        else if (type == 0) {
          // 4 - Hash?
          fm.skip(4);

          //  4 - Filename Offset (relative to the start of this file data) (44)
          long filenameOffset = offset + fm.readInt();
          FieldValidator.checkOffset(filenameOffset, arcSize);

          // 4 - Data Length (including all these headers)
          int length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          // X - Other stuff (similar to the TEX2 archives)
          fm.relativeSeek(filenameOffset);

          // 84 - Filename (null terminated, filled with nulls)
          String filename = fm.readNullString();
          FieldValidator.checkFilename(filename);
          filename += ".sgp";

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
          realNumFiles++;

          fm.seek(offset + length);
        }
        else {
          ErrorLogger.log("[SGP2] Unknown block type: " + type);
        }

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
