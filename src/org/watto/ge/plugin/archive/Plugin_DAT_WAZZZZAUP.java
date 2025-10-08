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
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_DAT_WAZZZZAUP extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_DAT_WAZZZZAUP() {

    super("DAT_WAZZZZAUP", "DAT_WAZZZZAUP");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Alchemy Mysteries: Prague Legends",
        "Cursed",
        "Sinister City");
    setExtensions("dat"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    setTextPreviewExtensions("sound", "ispy", "overlay", "particle", "scene", "material"); // LOWER CASE

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
      if (fm.readString(10).equals("wazzzzaup!")) {
        rating += 50;
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

      ExporterPlugin exporter = Exporter_ZLib.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 10 - Header (wazzzzaup!)
      fm.skip(10);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Unknown
      fm.skip(4);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      int[] lengths = new int[numFiles];
      for (int i = 0; i < numFiles; i++) {
        // 4 - Decompressed File Length
        int decompLength = fm.readInt();
        FieldValidator.checkLength(decompLength);

        // 4 - Compressed File Length (null if not compressed)
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 8 - null
        fm.skip(8);

        // 4 - Filename Length
        int filenameLength = fm.readInt();
        FieldValidator.checkFilenameLength(filenameLength);

        // X - Filename
        String filename = fm.readString(filenameLength);

        // 4 - Directory Name Length (can be null)
        int dirNameLength = fm.readInt();
        FieldValidator.checkFilenameLength(dirNameLength + 1);

        // X - Directory Name (starts with "." or ".\" or ".\\")
        String dirName = fm.readString(dirNameLength);

        if (dirNameLength >= 1 && dirName.charAt(0) == '.') {
          if (dirNameLength >= 2 && dirName.charAt(1) == '\\') {
            if (dirNameLength >= 3 && dirName.charAt(2) == '\\') {
              dirName = dirName.substring(3);
              dirNameLength = dirName.length();
            }
            else {
              dirName = dirName.substring(2);
              dirNameLength = dirName.length();
            }
          }
          else {
            dirName = dirName.substring(1);
            dirNameLength = dirName.length();
          }
        }

        if (dirNameLength > 0) {
          filename = dirName + '\\' + filename;
        }

        if (length == 0 || (length == decompLength)) {
          // raw file

          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, 0, decompLength);

          lengths[i] = decompLength;
        }
        else {
          // compressed

          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, 0, length, decompLength, exporter);

          lengths[i] = length;
        }

        TaskProgressManager.setValue(i);
      }

      // Calculate the offsets
      long offset = fm.getOffset();

      for (int i = 0; i < numFiles; i++) {
        FieldValidator.checkOffset(offset, arcSize);
        resources[i].setOffset(offset);
        offset += lengths[i] + 4;
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
