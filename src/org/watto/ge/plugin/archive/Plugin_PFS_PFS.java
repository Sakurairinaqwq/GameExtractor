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
import org.watto.ge.plugin.exporter.Exporter_XOR_RepeatingKey;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_PFS_PFS extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_PFS_PFS() {

    super("PFS_PFS", "PFS_PFS");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("3D Puzzle Venture");
    setExtensions("pfs"); // MUST BE LOWER CASE
    setPlatforms("PC");

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
      if (fm.readString(3).equals("PFS")) {
        rating += 50;
      }

      long arcSize = fm.getLength();

      // File Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // File Length
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

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false, 128); // small quick reads

      long arcSize = fm.getLength();

      // 3 - Header (PFS)
      fm.skip(3);

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      int[] xorKey = new int[] { 69, 138, 21, 42, 84, 168, 81, 162 };

      // Loop through directory
      int realNumFiles = 0;
      while (fm.getOffset() < arcSize) {
        int startOffset = (int) fm.getOffset();

        // 4 - File Length (data only)
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - File Length (including all these header fields)
        //int filenameLength = fm.readInt() - length - 8;
        //FieldValidator.checkLength(filenameLength, arcSize);
        int entryLength = fm.readInt();
        FieldValidator.checkLength(entryLength, arcSize);

        // X - Filename (encrypted)
        // 1 - null Filename Terminator
        //fm.skip(filenameLength);
        fm.skip(1);
        fm.readNullString();

        // set the actual length, as files with multiple blocks have a bit extra
        length = (int) (entryLength - (fm.getOffset() - startOffset));

        //int filenameLength = entryLength - length - 8 - 1;
        //System.out.println(filenameLength);

        // X - File Data (encrypted)
        long offset = fm.getOffset();
        fm.seek(startOffset + entryLength);

        String filename = Resource.generateFilename(realNumFiles);

        int numBlocks = (length / 32769);
        if (length % 32769 != 0) {
          numBlocks++;
        }

        ExporterPlugin exporter = new Exporter_XOR_RepeatingKey(xorKey);

        if (numBlocks == 1) {
          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length, length, exporter);
          realNumFiles++;
        }
        else {
          // multiple blocks

          long[] blockOffsets = new long[numBlocks];
          long[] blockLengths = new long[numBlocks];

          long currentOffset = offset;
          int remainingLength = length;
          for (int b = 0; b < numBlocks; b++) {
            blockOffsets[b] = currentOffset;

            int blockLength = remainingLength;
            if (blockLength > 32769) {
              blockLength = 32768;
            }

            blockLengths[b] = blockLength;

            remainingLength -= blockLength;
            remainingLength--; // remove the "null" byte between each offset

            currentOffset += blockLength + 1; // remove the "null" byte between each offset
          }

          ExporterPlugin blockExporter = new BlockExporterWrapper(exporter, blockOffsets, blockLengths, blockLengths);

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length, length, blockExporter);
          realNumFiles++;
        }

        TaskProgressManager.setValue(offset);
      }

      resources = resizeResources(resources, realNumFiles);

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

    if (headerInt1 == 1397116231) {
      return "gmfs";
    }
    else if (headerInt1 == 1397116487) {
      return "gnfs";
    }
    else if (headerBytes[0] == 80 && headerBytes[1] == 83 && headerBytes[2] == 72) {
      return "psh";
    }
    else if (headerShort1 == -257) {
      return "xml";
    }

    return null;
  }

}
