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
public class Plugin_XPR_XPR1 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_XPR_XPR1() {

    super("XPR_XPR1", "XPR_XPR1");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Generic XBox Archive",
        "Unreal Tournament 2");
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
      if (fm.readString(4).equals("XPR1")) {
        rating += 50;
      }

      long arcSize = fm.getLength();

      // Archive Size
      if (FieldValidator.checkEquals(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // first file offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // Number Of Files
      int numFiles = fm.readInt();
      if (FieldValidator.checkNumFiles(numFiles)) {
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
  
  **********************************************************************************************
  **/
  @Override
  public Resource[] read(File path) {
    try {

      addFileTypes();

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 4 - Header (XPR1)
      // 4 - Archive Size
      fm.skip(8);

      // 4 - First File Offset (2048)
      int relOffset = fm.readInt();
      FieldValidator.checkOffset(relOffset, arcSize);

      // 4 - Number Of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // X - pointer directory
      fm.skip(4);
      int firstDataEntryOffset = fm.readInt() + 12;
      fm.skip((numFiles - 1) * 8 + 4);

      // Loop through filename directory
      String[] names = new String[numFiles];
      for (int i = 0; i < numFiles; i++) {
        // X - Filename (null)
        names[i] = fm.readNullString();
      }

      // X - null padding
      fm.seek(firstDataEntryOffset);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {
        // 2 - Unknown (1)
        // 2 - Unknown (4)
        fm.skip(4);

        // 8 - Offset [+firstFileOffset]
        long offset = (int) fm.readLong() + relOffset;
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - Flags
        //   4 bits - dma
        //   4 bits - dimensions // Is this a 1D, 2D, or 3D texture
        //   8 bits - format     // The format of the texture data (see the table below)
        //   4 bits - levels     // Number of mipmap levels
        //   4 bits - width      // Width of the texture in the following format: actual_width = (1 << width)
        //   4 bits - height     // Height of the texture in the following format: actual_height = (1 << height)
        //   4 bits - depth      // Depth of the texture (for 3D textures) in the following format: actual_depth = (1 << depth)
        fm.skip(1);

        int imageFormat = ByteConverter.unsign(fm.readByte());

        int byte3 = ByteConverter.unsign(fm.readByte());
        int width = 1 << ((byte3 >> 4) & 15);
        int numMipmaps = (byte3 & 15);

        int byte4 = ByteConverter.unsign(fm.readByte());
        int height = 1 << (byte4 & 15);

        // 4 - Alternate Size (if the image isn't a power of 2)
        fm.skip(4);

        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A1R5G5B5       0x02  2
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_X1R5G5B5       0x03  3
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A4R4G4B4       0x04  4
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_R5G6B5         0x05  5
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A8R8G8B8       0x06  6
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_X8R8G8B8       0x07  7
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_I8_A8R8G8B8    0x0B  11
        // NV097_SET_TEXTURE_FORMAT_COLOR_L_DXT1_A1R5G5B5   0x0C  12
        // NV097_SET_TEXTURE_FORMAT_COLOR_L_DXT23_A8R8G8B8  0x0E  14
        // NV097_SET_TEXTURE_FORMAT_COLOR_L_DXT45_A8R8G8B8  0x0F  15
        // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_R5G6B5   0x11  17
        // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_A8R8G8B8 0x12  18
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A8             0x19  25
        // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_X8R8G8B8 0x1E  30
        // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_DEPTH_Y16_FIXED 0x30  48
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A8B8G8R8       0x3A  58
        // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_A8B8G8R8 0x3F  63
        String imageFormatString = null;
        if (imageFormat == 2) {
          imageFormatString = "ARGB1555";
        }
        else if (imageFormat == 4) {
          imageFormatString = "ARGB4444";
        }
        else if (imageFormat == 5) {
          imageFormatString = "RGB565";
        }
        else if (imageFormat == 6) {
          imageFormatString = "ARGB";
        }
        else if (imageFormat == 12) {
          imageFormatString = "DXT1";
        }
        else if (imageFormat == 14) {
          imageFormatString = "DXT3";
        }
        else if (imageFormat == 15) {
          imageFormatString = "DXT5";
        }
        else {
          imageFormatString = "" + imageFormat;
        }

        String filename = names[i];

        //System.out.println(width + "\t" + height + "\t" + filename);

        //path,id,name,offset,length,decompLength,exporter
        Resource resource = new Resource(path, filename, offset);
        resource.addProperty("Width", width);
        resource.addProperty("Height", height);
        resource.addProperty("ImageFormat", imageFormatString);
        resource.addProperty("MipmapCount", numMipmaps);
        resources[i] = resource;

        TaskProgressManager.setValue(i);
      }

      fm.close();

      calculateFileSizes(resources, arcSize);

      return resources;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

}
