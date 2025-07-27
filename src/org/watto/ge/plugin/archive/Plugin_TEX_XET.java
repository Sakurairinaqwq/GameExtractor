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

import org.watto.datatype.Archive;
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_TEX_XET extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_TEX_XET() {

    super("TEX_XET", "TEX_XET");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Grand Theft Auto: Liberty City Stories");
    setExtensions("tex"); // MUST BE LOWER CASE
    setPlatforms("PSP");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("tex_tex", "Texture Image", FileType.TYPE_IMAGE));

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
      if (fm.readInt() == 7628152) { // "xet" + null
        rating += 50;
      }

      if (fm.readInt() == 0) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Archive Size
      if (FieldValidator.checkEquals(fm.readInt(), arcSize)) {
        rating += 5;
      }

      fm.skip(20);

      if (fm.readInt() == 6) {
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

      // 4 - Header ("xet" + null)
      // 4 - null
      // 4 - File Length
      // 4 - Unknown
      // 4 - Unknown
      // 4 - Unknown (12/32)
      // 8 - null
      // 4 - Unknown (6)
      // 4 - null
      // 4 - Unknown
      fm.skip(44);

      // 4 - Last Directory Entry Offset [-8] (numFiles = ((ThisField-8+84)-56)/100)
      //int numFiles = ((fm.readInt() - 8 + 84) - 56) / 100;
      //FieldValidator.checkNumFiles(numFiles);
      int numFiles = Archive.getMaxFiles();

      // 8 - Hash
      fm.skip(8);

      // lets read in the full directory so we can skip around.
      // include the 56-byte header so that we don't need to calculate adjust offsets when jumping
      fm.relativeSeek(0);
      //byte[] dirBytes = fm.readBytes((numFiles * 100) + 56);
      byte[] dirBytes = fm.readBytes(10000); // guess max

      fm.close();
      fm = new FileManipulator(new ByteBuffer(dirBytes));
      fm.seek(56); // back to the first entry in the directory

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      int realNumFiles = 0;
      int earliestOffset = (int) arcSize;
      long[] offsets = new long[numFiles];
      while (fm.getOffset() < (earliestOffset - 15)) { // as we can have up to 16 bytes of padding before the first file
        //System.out.println(fm.getOffset() + "\t" + earliestOffset);
        // 4 - Metadata Offset for this Image
        int metadataOffset = fm.readInt();

        boolean exitBigLoop = false;
        while (metadataOffset == 0) {
          // this is a metadata entry - skip it, we're only looking for data entries
          fm.skip(12);

          if (fm.getOffset() >= (earliestOffset - 15)) {
            exitBigLoop = true;
            break;
          }

          // 4 - Metadata Offset for this Image
          metadataOffset = fm.readInt();
        }

        if (exitBigLoop) {
          break;
        }

        FieldValidator.checkOffset(metadataOffset);

        // now we should be at a data entry

        // 4 - Unknown (32)
        // 4 - Unknown
        // 4 - Unknown
        fm.skip(12);

        // 64 - Image Name (null terminated, filled with nulls)
        String filename = fm.readNullString(64);
        FieldValidator.checkFilename(filename);
        filename += ".tex_tex";

        // 4 - Unknown (1)
        fm.skip(4);

        // now read the metadata for the image
        long currentOffset = fm.getOffset();

        fm.seek(metadataOffset);

        // 4 - null
        fm.skip(4);

        // 4 - Image Data Offset
        int offset = fm.readInt();
        if (offset < earliestOffset) {
          earliestOffset = offset;
          //System.out.println("\t" + earliestOffset);
        }
        offsets[realNumFiles] = offset;

        // 2 - Image Width/Height? (if this is a 4bpp image, this number needs to be doubled)
        fm.skip(2);

        // 1 - Image Width [1<<x] (ie 6 = 1<<6 = 64)
        int width = fm.readByte();
        FieldValidator.checkRange(width, 0, 15); // guess max 32768*32768
        width = 1 << width;

        // 1 - Image Height [1<<x] (ie 6 = 1<<6 = 64)
        int height = fm.readByte();
        FieldValidator.checkRange(height, 0, 15); // guess max 32768*32768
        height = 1 << height;

        // 1 - Bits Per Pixel (4/8)
        int bpp = fm.readByte();
        FieldValidator.checkRange(bpp, 4, 32);

        // 1 - Number of Mipmaps?
        // 1 - Unknown (4)
        // 1 - Unknown
        fm.skip(3);

        /*
        // work out the length
        int length = 0;
        
        if (bpp == 8) {
          length += 1024; // 256 colors
        }
        else if (bpp == 4) {
          length += 64; // 16 colors
        }
        
        boolean rectangle = width != height;
        
        int mipmapWidth = width;
        int mipmapHeight = height;
        if (bpp == 8) {
          while (mipmapWidth >= 8 && mipmapHeight >= 8) {
            length += mipmapWidth * mipmapHeight;
            mipmapWidth /= 2;
            mipmapHeight /= 2;
          }
          // add 4x4 and 2x2 (which are 64 bytes each) and 1x1 (32 bytes)
          if (rectangle) {
            length += 32 + 32 + 32;
          }
          else {
            length += 64 + 64 + 32;
          }
        }
        else if (bpp == 4) {
          while (mipmapWidth > 16 && mipmapHeight > 16) {
            length += mipmapWidth * mipmapHeight / 2; // 1 byte stores 2 pixels
            mipmapWidth /= 2;
            mipmapHeight /= 2;
          }
          // add 16x16 (stored as 256) and 8x8 and 4x4 (stored as 128 each) and 2x2 (64 bytes) and 1x1 (32 bytes)
          if (rectangle) {
            int largestDimension = mipmapWidth;
            if (mipmapHeight > largestDimension) {
              largestDimension = mipmapHeight;
            }
            length += (largestDimension * largestDimension / 2) + 256 + 64 + 64 + 32 + 32;
          }
          else {
            length += 256 + 64 + 64 + 64 + 32;
          }
        }
        else if (bpp == 32) {
          while (mipmapWidth >= 2 && mipmapHeight >= 2) {
            length += mipmapWidth * mipmapHeight * 4;
            mipmapWidth /= 2;
            mipmapHeight /= 2;
          }
          // add 1x1 (16 bytes)
          length += 16;
        }
        */

        // go back to the end of the current data entry, ready to find the next one
        fm.seek(currentOffset);

        //path,name,offset,length,decompLength,exporter
        Resource resource = new Resource(path, filename, offset);
        resource.addProperty("Width", width);
        resource.addProperty("Height", height);
        resource.addProperty("BitsPerPixel", bpp);
        resources[realNumFiles] = resource;

        realNumFiles++;

        TaskProgressManager.setValue(realNumFiles);
      }

      fm.close();

      resources = resizeResources(resources, realNumFiles);

      // work out the lengths
      long[] oldOffsets = offsets;
      offsets = new long[realNumFiles];
      System.arraycopy(oldOffsets, 0, offsets, 0, realNumFiles);
      Arrays.sort(offsets);

      HashMap<Integer, Integer> offsetLengthMap = new HashMap<Integer, Integer>(realNumFiles);
      for (int i = 0; i < realNumFiles - 1; i++) {
        offsetLengthMap.put((int) offsets[i], (int) (offsets[i + 1] - offsets[i]));
      }
      long endOfLastFile = arcSize - (8 + realNumFiles * 20); // remove the footer from the archive
      offsetLengthMap.put((int) offsets[realNumFiles - 1], (int) (endOfLastFile - offsets[realNumFiles - 1]));

      for (int i = 0; i < realNumFiles; i++) {
        Resource resource = resources[i];
        int length = offsetLengthMap.get((int) resource.getOffset());
        resource.setLength(length);
        resource.setDecompressedLength(length);
      }

      /*
      // work out the footer length
      long largestOffset = 0;
      long lengthOfLargest = 0;
      for (int i = 0; i < realNumFiles; i++) {
        long offset = resources[i].getOffset();
        if (offset > largestOffset) {
          largestOffset = offset;
          lengthOfLargest = resources[i].getLength();
        }
      }
      
      int footerLength = (int) (arcSize - (largestOffset + lengthOfLargest));
      System.out.println(footerLength);
      */

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
