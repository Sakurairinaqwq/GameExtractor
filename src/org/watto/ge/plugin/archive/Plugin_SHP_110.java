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
import java.util.HashMap;

import org.watto.datatype.FileType;
import org.watto.datatype.Palette;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.PaletteManager;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_SHP_110 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_SHP_110() {

    super("SHP_110", "SHP_110");

    //         read write replace rename
    setProperties(true, false, false, false);

    setExtensions("shp");
    setGames("Panzer General",
        "Steel Panthers 2",
        "Steel Panthers 3");
    setPlatforms("PC");

    setFileTypes(new FileType("shp_tex", "SHP Image", FileType.TYPE_IMAGE));

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
      if (fm.readString(4).equals("1.10")) {
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
  
  **********************************************************************************************
  **/
  @Override
  public Resource[] read(File path) {
    try {

      addFileTypes();

      FileManipulator fm = new FileManipulator(path, false);

      // 4 - Version (1.10) - read as string
      fm.skip(4);

      // 4 - Number of Images
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      long arcSize = fm.getLength();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      int[] offsets = new int[(numFiles * 2) + 1]; // *2 to store the palette offsets as well (which can be scattered throughout the archive), +1 to store the first palette offset in the last entry, so we can calculate the sizes of the images
      int numOffsets = 0;
      for (int i = 0; i < numFiles; i++) {
        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - Palette Offset
        int paletteOffset = fm.readInt();
        FieldValidator.checkOffset(paletteOffset, arcSize);

        offsets[numOffsets++] = offset;
        offsets[numOffsets++] = paletteOffset;

        String filename = Resource.generateFilename(i) + ".shp_tex";

        //path,id,name,offset,length,decompLength,exporter
        Resource resource = new Resource(path, filename, offset);
        resource.addProperty("PaletteOffset", paletteOffset);
        resources[i] = resource;

        TaskProgressManager.setValue(i);
      }

      // store the arcSize so we can calculate all the lengths without a funny loop
      offsets[numOffsets] = (int) arcSize;

      // Calculate File Sizes
      Arrays.sort(offsets);

      HashMap<Integer, Integer> lengthMap = new HashMap<Integer, Integer>(numFiles);

      for (int i = 0; i < numOffsets; i++) {
        int thisOffset = offsets[i];
        int length = offsets[i + 1] - thisOffset;
        //System.out.println(thisOffset + "        IN ");
        lengthMap.put(thisOffset, length);
      }

      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        //System.out.println(resource.getOffset() + " OUT ");
        int length = lengthMap.get((int) resource.getOffset());
        resource.setLength(length);
        resource.setDecompressedLength(length);
      }

      // now go through and load all the palettes, ready for previews
      PaletteManager.clear();
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        int paletteOffset = Integer.parseInt(resource.getProperty("PaletteOffset"));
        fm.seek(paletteOffset);

        // 4 - Number of Colors
        int numColors = fm.readInt();
        FieldValidator.checkNumColors(numColors);

        // Palette
        int[] palette = new int[256];
        for (int c = 0; c < numColors; c++) {
          // 1 - Palette Index
          int index = ByteConverter.unsign(fm.readByte());

          // 1 - Red
          // 1 - Green
          // 1 - Blue
          int r = ByteConverter.unsign(fm.readByte()) * 4;
          int g = ByteConverter.unsign(fm.readByte()) * 4;
          int b = ByteConverter.unsign(fm.readByte()) * 4;

          // OUTPUT = ARGB
          palette[index] = ((r << 16) | (g << 8) | b | (255 << 24));
        }

        PaletteManager.addPalette(new Palette(palette));
        resource.addProperty("PaletteID", i);
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