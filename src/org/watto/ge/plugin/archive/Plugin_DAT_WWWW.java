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
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.BlockExporterWrapper;
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_DAT_WWWW extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_DAT_WWWW() {

    super("DAT_WWWW", "DAT_WWWW");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Enchantment: Secret Hideaway");
    setExtensions("dat"); // MUST BE LOWER CASE
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

      // Header
      if (fm.readString(4).equals("wwww")) {
        rating += 50;
      }

      fm.skip(4);

      if (fm.readInt() == 32) {
        rating += 5;
      }

      fm.skip(20);

      if (fm.readString(4).equals("PAMU")) {
        rating += 15;
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

      FileManipulator fm = new FileManipulator(path, false, 64); // small quick reads

      long arcSize = fm.getLength();

      // 4 - Header (wwww)
      // 4 - Unknown
      fm.skip(8);

      // 4 - PAMU Offset (32)
      int dirOffset = fm.readInt();
      FieldValidator.checkOffset(dirOffset, arcSize);

      // 4 - Unknown (64)
      // 4 - Unknown (3)
      // 12 - null
      fm.relativeSeek(dirOffset);

      // 4 - Header 2 (PAMU)
      // 4 - Unknown
      // 4 - Unknown (3)
      // 4 - Unknown
      fm.skip(16);

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      // Loop through directory
      int realNumFiles = 0;
      while (fm.getOffset() < arcSize) {
        // 4 - Unknown
        fm.skip(4);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // X - File Data
        long offset = fm.getOffset();
        fm.skip(length);

        String filename = Resource.generateFilename(realNumFiles);

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length);
        realNumFiles++;

        TaskProgressManager.setValue(offset);
      }

      resources = resizeResources(resources, realNumFiles);

      numFiles = realNumFiles;

      // now go through and look for compression
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];

        long length = resource.getLength();
        long offset = resource.getOffset();

        fm.seek(offset);

        TaskProgressManager.setValue(offset);

        // 4 - Decompressed Block Length
        int blockDecompLength = fm.readInt();

        // 4 - Compressed Block Length
        int blockLength = fm.readInt();

        // 1 - Compression Flag
        int compressionByte = fm.readByte();

        if (compressionByte == 120 && (blockLength > 0 && blockLength < length)) {
          // compressed in blocks

          if (length == blockLength + 8) {
            // a single block
            FieldValidator.checkLength(blockDecompLength);
            FieldValidator.checkLength(blockLength, arcSize);

            resource.setOffset(offset + 8);
            resource.setDecompressedLength(blockDecompLength);
            resource.setLength(blockLength);
            resource.setExporter(exporter);
          }
          else {
            // multiple blocks

            fm.relativeSeek(offset);

            int numBlocks = Archive.getMaxFiles(2);
            int realNumBlocks = 0;

            long[] blockOffsets = new long[numBlocks];
            long[] blockLengths = new long[numBlocks];
            long[] blockDecompLengths = new long[numBlocks];

            int remainingLength = (int) length;
            int totalDecompLength = 0;

            while (remainingLength > 0) {
              //System.out.println("Block at " + fm.getOffset());

              // 4 - Decompressed File Length?
              blockDecompLength = fm.readInt();
              FieldValidator.checkLength(blockDecompLength);
              totalDecompLength += blockDecompLength;

              // 4 - Compressed Chunk Length
              blockLength = fm.readInt();
              FieldValidator.checkLength(blockLength, arcSize);

              // X - File Data
              long blockOffset = fm.getOffset();
              fm.skip(blockLength);

              blockOffsets[realNumBlocks] = blockOffset;
              blockLengths[realNumBlocks] = blockLength;
              blockDecompLengths[realNumBlocks] = blockDecompLength;
              realNumBlocks++;

              remainingLength -= (blockLength + 8);
            }

            // shrink the arrays
            long[] oldBlockOffsets = blockOffsets;
            blockOffsets = new long[realNumBlocks];
            System.arraycopy(oldBlockOffsets, 0, blockOffsets, 0, realNumBlocks);

            long[] oldBlockLengths = blockLengths;
            blockLengths = new long[realNumBlocks];
            System.arraycopy(oldBlockLengths, 0, blockLengths, 0, realNumBlocks);

            long[] oldBlockDecompLengths = blockDecompLengths;
            blockDecompLengths = new long[realNumBlocks];
            System.arraycopy(oldBlockDecompLengths, 0, blockDecompLengths, 0, realNumBlocks);

            BlockExporterWrapper blockExporter = new BlockExporterWrapper(exporter, blockOffsets, blockLengths, blockDecompLengths);

            resource.setDecompressedLength(totalDecompLength);
            resource.setExporter(blockExporter);

          }

        }

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
