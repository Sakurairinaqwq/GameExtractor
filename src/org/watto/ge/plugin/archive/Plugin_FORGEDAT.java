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
import org.watto.io.converter.IntConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_FORGEDAT extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_FORGEDAT() {

    super("FORGEDAT", "FORGEDAT");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("For Honor");
    setExtensions("forge_dat", "dat"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("map", "Map Image", FileType.TYPE_IMAGE));

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

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readShort())) {
        rating += 5;
      }

      fm.skip(4);

      if (fm.readInt() == 0) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // First File Length
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

      FileManipulator fm = new FileManipulator(path, false, 256); // small quick reads

      long arcSize = fm.getLength();

      // 2 - Number Of Files
      short numFiles = fm.readShort();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      long offset = 2 + (numFiles * 12);
      long[] offsets = new long[numFiles];
      for (int i = 0; i < numFiles; i++) {
        offsets[i] = offset;

        // 4 - Hash?
        // 4 - null
        fm.skip(8);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        offset += length;

        TaskProgressManager.setValue(i);
      }

      // get the details of each file
      for (int i = 0; i < numFiles; i++) {
        fm.seek(offsets[i]);

        // 4 - Type ID
        long type = IntConverter.unsign(fm.readInt());

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - Filename Length
        int filenameLength = fm.readInt();
        FieldValidator.checkFilenameLength(filenameLength);

        // X - Filename
        String filename = fm.readString(filenameLength);

        if (type == 1096652136) {
          filename += ".mdl";
        }
        else if (type == 2093723172) {
          filename += ".skel";
        }
        else if (type == 401192866) {
          filename += ".dds";
        }
        else if (type == 1342123676) {
          filename += ".bone";
        }
        else if (type == 3273115781l) {
          filename += ".mtl";
        }
        else if (type == 2999360920l) {
          filename += ".mtl_i";
        }
        else if (type == 2629974677l) {
          filename += ".cloth";
        }
        else if (type == 2560476850l) {
          filename += ".map_spec";
        }
        else if (type == 2729961751l) {
          filename += ".map";
        }
        else if (type == 1869929908) {
          filename += ".diffnormspec";
        }
        else {
          filename += "." + type;
        }

        offset = fm.getOffset();

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length);

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
