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
import org.watto.io.converter.ByteConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_XPR_XPR0 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_XPR_XPR0() {

    super("XPR_XPR0", "XPR_XPR0");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Far Cry");
    setExtensions("xpr");
    setPlatforms("XBox");

    setFileTypes(new FileType("dxt", "DXT Image", FileType.TYPE_IMAGE));

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
      if (fm.readString(4).equals("XPR0")) {
        rating += 50;
      }

      long arcSize = fm.getLength();

      // Archive Size
      if (FieldValidator.checkEquals(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // First File Offset
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

      // 4 - Header (XPR0)
      // 4 - Archive Size
      fm.skip(8);

      // 4 - First File Offset (2048)
      int firstFileOffset = fm.readInt();
      FieldValidator.checkOffset(firstFileOffset, arcSize);

      int numFiles = firstFileOffset / 20; /// approximate
      int realNumFiles = 0;

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      int[] powers = new int[] { 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096 };
      while (fm.getOffset() < firstFileOffset) {
        // 2 - Unknown (1)
        // 2 - Unknown (4)
        int hasMore = fm.readInt();
        if (hasMore == -1) {
          break;
        }

        // 8 - Offset [+firstFileOffset]
        long offset = fm.readLong() + firstFileOffset;
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - Flags
        fm.skip(2);

        int width = powers[(ByteConverter.unsign(fm.readByte()) >> 4)];
        int height = powers[(ByteConverter.unsign(fm.readByte()) & 15)];

        // 4 - Unknown
        fm.skip(4);

        String filename = Resource.generateFilename(realNumFiles) + ".dxt";

        //path,name,offset,length,decompLength,exporter
        //resources[realNumFiles] = new Resource(path, filename, offset);
        Resource resource = new Resource(path, filename, offset);
        resource.addProperty("Width", width);
        resource.addProperty("Height", height);
        resources[realNumFiles] = resource;

        TaskProgressManager.setValue(realNumFiles);
        realNumFiles++;
      }

      resources = resizeResources(resources, realNumFiles);
      calculateFileSizes(resources, arcSize);

      fm.close();

      return resources;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

}
