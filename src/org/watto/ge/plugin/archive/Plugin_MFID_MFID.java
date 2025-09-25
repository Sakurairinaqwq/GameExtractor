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
import java.util.Arrays;

import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_MFID_MFID extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_MFID_MFID() {

    super("MFID_MFID", "MFID_MFID");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Shadow Hearts");
    setExtensions("mfid"); // MUST BE LOWER CASE
    setPlatforms("PS2");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    //setTextPreviewExtensions("colours", "rat", "screen", "styles"); // LOWER CASE

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

      // Header
      if (fm.readString(4).equals("MFID")) {
        rating += 50;
      }

      if (fm.readInt() == 64) {
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

      // 4 - Header (MFID)
      fm.skip(4);

      // 4 - First File Offset
      int numFiles = (fm.readInt() / 4) - 1;
      FieldValidator.checkNumFiles(numFiles);

      fm.relativeSeek(4);

      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      int realNumFiles = 0;
      int[] offsets = new int[numFiles];
      for (int i = 0; i < numFiles; i++) {

        // 4 - File Offset (can be -1 or null or arcLength)
        int offset = fm.readInt();
        if (offset == -1 || offset == 0 || offset == arcSize) {
          continue;
        }
        FieldValidator.checkOffset(offset, arcSize);

        offsets[realNumFiles] = offset;
        realNumFiles++;

        TaskProgressManager.setValue(i);
      }

      numFiles = realNumFiles;

      int[] oldOffsets = offsets;
      offsets = new int[numFiles];
      System.arraycopy(oldOffsets, 0, offsets, 0, numFiles);

      Arrays.sort(offsets);

      Resource[] resources = new Resource[numFiles];
      for (int i = 0; i < numFiles; i++) {
        int offset = offsets[i];

        String filename = Resource.generateFilename(i);

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset);

        TaskProgressManager.setValue(i);
      }

      calculateFileSizes(resources, arcSize);

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

    if (headerInt1 == 1129535557) {
      return "exsc";
    }
    else if (headerInt1 == 1145651789) {
      return "mbid";
    }
    else if (headerInt1 == 1145652557) {
      return "meid";
    }
    else if (headerInt1 == 1145656141) {
      return "msid";
    }
    else if (headerInt1 == 1162690883) {
      return "came";
    }
    else if (headerInt1 == 1280134721) {
      return "anml";
    }
    else if (headerInt1 == 1381256773) {
      return "entr";
    }
    else if (headerInt1 == 1414745421) {
      return "mmst";
    }
    else if (headerInt1 == 1448100173) {
      return "mapv";
    }
    else if (headerInt1 == 1668507763) {
      return "script";
    }

    return null;
  }

}
