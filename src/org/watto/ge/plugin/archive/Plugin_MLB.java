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
import org.watto.ge.helper.PaletteManager;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_MLB extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_MLB() {

    super("MLB", "MLB");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Screamer");
    setExtensions("mlb"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

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

      int flags = fm.readInt();
      if (flags == 16 || flags == 64) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Directory Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

      if (FieldValidator.checkWidth(fm.readInt())) {
        rating += 5;
      }

      if (FieldValidator.checkHeight(fm.readInt())) {
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
      PaletteManager.clear();

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // Don't know how to read the individual sprites, so don't do this for now.
      /*
      // get the first offset
      // 4 - Flags? (16/64)
      fm.skip(4);
      
      // 4 - Block Data Entry Offset
      int blockOffset = fm.readInt();
      int numFiles = (blockOffset / 16) - 1;
      FieldValidator.checkNumFiles(numFiles);
      
      // back to the start
      fm.relativeSeek(0);
      
      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);
      
      // Loop through directory
      
      for (int i = 0; i < numFiles; i++) {
        // 4 - Flags? (16/64)
        fm.skip(4);
        //System.out.println(fm.readInt());
      
        // 4 - Block Data Entry Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);
      
        // 4 - Image Width?
        int width = fm.readInt();
        //FieldValidator.checkWidth(width);
      
        // 4 - Image Height?
        int height = fm.readInt();
        //FieldValidator.checkHeight(height);
      
        String filename = Resource.generateFilename(i) + ".mlb_tex";
      
        //path,name,offset,length,decompLength,exporter
        Resource resource = new Resource(path, filename, offset);//, 256 * 512);
        resource.addProperty("Width", width);
        resource.addProperty("Height", height);
        resources[i] = resource;
      
        TaskProgressManager.setValue(i);
      }
      
      long[] offsets = new long[numFiles];
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        long offset = resource.getOffset();
      
        if (offset == 0) {
          offsets[i] = 0;
          continue;
        }
      
        fm.relativeSeek(offset);
      
        // 4 - Number of Blocks
        int numBlocks = fm.readInt();
        FieldValidator.checkRange(numBlocks, 1, 100);//guess
      
        // 4 - Unknown
        fm.skip(4);
        //System.out.println(fm.readInt());
      
        // for each block (last to first)
        long smallestOffset = arcSize;
        for (int b = 0; b < numBlocks; b++) {
          // 4 - Block Data Offset (for the first block)
          offset = fm.readInt();
          if (offset > 0 && offset < smallestOffset) {
            smallestOffset = offset;
          }
        }
      
        offsets[i] = smallestOffset;
      
        resource.setOffset(smallestOffset);
      }
      
      // work out the file lengths
      Arrays.sort(offsets);
      
      HashMap<Long, Long> lengthMap = new HashMap<Long, Long>(numFiles);
      for (int i = 0; i < numFiles - 1; i++) {
        long thisOffset = offsets[i];
        long length = offsets[i + 1] - thisOffset;
        lengthMap.put(thisOffset, length);
      }
      long lastOffset = offsets[numFiles - 1];
      long lastLength = arcSize - lastOffset;
      lengthMap.put(lastOffset, lastLength);
      
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        long offset = resource.getOffset();
      
        long length = lengthMap.get(offset);
        if (offset == 0) {
          length = 0;
        }
      
        resource.setLength(length);
        resource.setDecompressedLength(length);
      }
      
      // TEMP FOR DEBUGGING FILE MAP1.MLB
      resources[0].setOffset(13924);
      resources[0].setLength(2686976);
      resources[0].setProperty("Height", "" + 10496);
      */

      // get the first offset
      // 4 - Flags? (16/64)
      fm.skip(4);

      // 4 - Block Data Entry Offset
      int blockOffset = fm.readInt();
      FieldValidator.checkOffset(blockOffset, arcSize);
      fm.seek(blockOffset);

      // read through the blocks until the end marker
      // 4 - Number of Blocks
      int numBlocks = fm.readInt();

      while (numBlocks != -1 && numBlocks != 0) {
        // 4 - Unknown
        // for each block
        //   4 - Block Data Offset
        fm.skip(4 + numBlocks * 4);

        // 4 - Number of Blocks
        numBlocks = fm.readInt();
      }

      int remainingLength = (int) (arcSize - fm.getOffset());

      int width = 256;
      int height = remainingLength / width;

      remainingLength = width * height;

      Resource[] resources = new Resource[1];
      TaskProgressManager.setMaximum(1);

      long offset = arcSize - remainingLength;

      String filename = Resource.generateFilename(0) + ".mlb_tex";

      //path,name,offset,length,decompLength,exporter
      Resource resource = new Resource(path, filename, offset, remainingLength);
      resource.addProperty("Width", width);
      resource.addProperty("Height", height);
      resources[0] = resource;

      TaskProgressManager.setValue(0);

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
