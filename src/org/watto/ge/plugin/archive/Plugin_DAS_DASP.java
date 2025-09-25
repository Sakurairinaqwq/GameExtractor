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
public class Plugin_DAS_DASP extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_DAS_DASP() {

    super("DAS_DASP", "DAS_DASP");

    //         read write replace rename
    setProperties(true, false, false, false);

    setExtensions("das");
    setGames("Normality");
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("das_tex", "Texture Image", FileType.TYPE_IMAGE));

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
      if (fm.readString(4).equals("DASP")) {
        rating += 50;
      }

      fm.skip(4);

      if (fm.readInt() == 40) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Directory Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

      fm.skip(4);

      // Directory Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

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
  
  **********************************************************************************************
  **/
  @SuppressWarnings("unused")
  @Override
  public Resource[] read(File path) {
    try {

      addFileTypes();

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 4 - Header (DASP)
      // 4 - Unknown
      fm.skip(8);

      // 4 - Header Length (40)
      int dirOffset = fm.readInt();
      FieldValidator.checkOffset(dirOffset, arcSize);

      // 4 - Color Palette Offset
      int colorPaletteOffset = fm.readInt();
      FieldValidator.checkOffset(colorPaletteOffset, arcSize);

      // 4 - Unknown
      fm.skip(4);

      // 4 - Filename Directory Offset
      int filenameDirOffset = fm.readInt();
      FieldValidator.checkOffset(filenameDirOffset, arcSize);

      // 4 - Filename Directory Length
      // 4 - null
      // 4 - Unknown
      // 4 - Unknown
      fm.seek(colorPaletteOffset);

      // 768 - Color Palette (RGB)
      PaletteManager.clear();

      int colorCount = 256;
      int[] palette = new int[colorCount];

      for (int i = 0; i < colorCount; i++) {
        // INPUT = RGB

        // colors need to be doubled otherwise they're too dark
        int r = ByteConverter.unsign(fm.readByte()) * 2;
        int g = ByteConverter.unsign(fm.readByte()) * 2;
        int b = ByteConverter.unsign(fm.readByte()) * 2;
        int a = 255;

        // OUTPUT = ARGB
        palette[i] = ((r << 16) | (g << 8) | b | (a << 24));
      }

      PaletteManager.addPalette(new Palette(palette));

      fm.seek(filenameDirOffset);

      int numFiles = (colorPaletteOffset - dirOffset) / 8; //max

      // 2 - Unknown
      // 2 - Unknown
      fm.skip(4);

      /*
      HashMap<Integer, String> names = new HashMap<Integer, String>();
      int numNames = 0;
      while (fm.getOffset() < arcSize) {
        // 2 - Entry Length (including this field)
        fm.skip(2);
      
        // 2 - File ID
        int fileID = fm.readShort();
      
        // X - Code String
        // 1 - null Code String Terminator
        String code = fm.readNullString();
      
        // X - Text String
        // 1 - null Text String Terminator
        String value = fm.readNullString();
      
        names.put(fileID, code + "_(" + value + ")");
        numNames++;
      }
      */
      String[] names = new String[numFiles];
      int numNames = 0;
      while (fm.getOffset() < arcSize) {
        // 2 - Entry Length (including this field)
        fm.skip(2);

        // 2 - File ID
        int fileID = fm.readShort();

        // X - Code String
        // 1 - null Code String Terminator
        String code = fm.readNullString();

        // X - Text String
        // 1 - null Text String Terminator
        String value = fm.readNullString();

        names[numNames] = code + "_(" + value + ")";
        numNames++;
      }

      fm.seek(dirOffset);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      int realNumFiles = 0;
      int[] offsets = new int[numFiles];
      for (int i = 0; i < numFiles; i++) {
        // 4 - File Offset (can be null)
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - Unknown
        fm.skip(4);

        if (offset == 0) {
          continue;
        }

        TaskProgressManager.setValue(i);

        offsets[realNumFiles] = offset;

        String filename = names[realNumFiles] + ".das_tex";//Resource.generateFilename(i);

        //path,id,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset);
        realNumFiles++;
      }

      resources = resizeResources(resources, realNumFiles);

      numFiles = realNumFiles;

      // work out the file lengths
      int[] oldOffsets = offsets;
      offsets = new int[numFiles + 1];
      System.arraycopy(oldOffsets, 0, offsets, 0, numFiles);
      offsets[numFiles] = filenameDirOffset;
      Arrays.sort(offsets);

      HashMap<Integer, Integer> lengths = new HashMap<Integer, Integer>(numFiles);
      for (int i = 0; i < numFiles; i++) {
        int offset = offsets[i];
        int length = offsets[i + 1] - offset;
        lengths.put(offset, length);
      }

      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        int length = lengths.get((int) resource.getOffset());
        resource.setLength(length);
        resource.setDecompressedLength(length);
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