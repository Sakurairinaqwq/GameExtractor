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
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_IMG_6 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_IMG_6() {

    super("IMG_6", "IMG_6");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Grand Theft Auto: Liberty City Stories");
    setExtensions("img"); // MUST BE LOWER CASE
    setPlatforms("PSP");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("tex", "Texture Archive", FileType.TYPE_ARCHIVE));

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

      // Header
      if (fm.readString(3).equals("xet")) {
        rating += 20;
      }

      if (fm.readInt() == 0) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // File Length
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

      FileManipulator fm = new FileManipulator(path, false, 12); // small quick reads

      long arcSize = fm.getLength();

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      // Loop through directory
      int realNumFiles = 0;
      while (fm.getOffset() < arcSize) {
        long offset = fm.getOffset();

        // 4 - Header (sometimes it's a reverse ASCII string like "xet" or "ldm")
        int header = fm.readInt();
        String extension = null;
        if (header == 1634625901) {
          extension = ".anim";
        }
        else if (header == 1668246578) {
          extension = ".col2";
        }
        else if (header == 7169132) {
          extension = ".mdl";
        }
        else if (header == 7628152) {
          extension = ".tex";
        }

        // 4 - null
        fm.skip(4);

        // 4 - File Length (including these header fields)
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // X - File Data
        // X - null Padding to a multiple of 2048 bytes

        int nextOffset = (int) (offset + length);
        nextOffset += calculatePadding(nextOffset, 2048);
        fm.seek(nextOffset);

        String filename = Resource.generateFilename(realNumFiles);
        if (extension != null) {
          filename += extension;
        }

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

    if (headerInt1 == 1634625901) {
      return "anim";
    }
    else if (headerInt1 == 1668246578) {
      return "col2";
    }
    else if (headerInt1 == 7169132) {
      return "mdl";
    }
    else if (headerInt1 == 7628152) {
      return "tex";
    }

    return null;
  }

}
