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
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_XOM_MOIK extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_XOM_MOIK() {

    super("XOM_MOIK", "XOM_MOIK");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Worms Ultimate Mayhem");
    setExtensions("xom"); // MUST BE LOWER CASE
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
      if (fm.readString(4).equals("MOIK")) {
        rating += 50;
      }

      fm.skip(20);

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
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

      //ExporterPlugin exporter = Exporter_ZLib.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 4 - Header (MOIK)
      // 4 - Version? (2) (BIG)
      // 16 - null
      fm.skip(24);

      // 4 - Number of Types
      int numTypes = fm.readInt();
      FieldValidator.checkNumFiles(numTypes);

      // 4 - Number of Files
      int numFiles = fm.readInt() + 1; // +1 to add the Strings table as a file
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Unknown
      // 28 - null
      fm.skip(32);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Read the Types directory, and store them against the filenames
      String[] names = new String[numFiles];

      int currentFile = 1; // 1, because the first file will be the strings table
      names[0] = Resource.generateFilename(0) + ".strings";

      //
      // Even though we store the filenames here, we don't use them, as the Types aren't always in the right order, and I don't know how to
      // get them in the right order, which is very disappointing, as we can't assign proper types to the files.
      //

      for (int i = 0; i < numTypes; i++) {
        // 4 - Header (TYPE)
        // 4 - Unknown (0/2)
        fm.skip(8);

        // 4 - Number of Files of this Type
        int numFilesOfThisType = fm.readInt();
        FieldValidator.checkNumFiles(numFilesOfThisType + 1); // allow nulls

        // 4 - null
        // 16 - GUID String?
        fm.skip(20);

        // 32 - Type Name (null terminated, filled with nulls)
        String typeName = fm.readNullString(32);

        /*
        // some types are just attributes, not files, so exclude them
        if (typeName.equals("XAlphaTest") || typeName.equals("XZBufferWriteEnable") || typeName.equals("XDepthTest") || typeName.equals("XCullFace") || typeName.equals("XLightingEnable") || typeName.equals("XBlendModeGL")) {
          continue;
        }
        */

        // store the filenames
        for (int j = 0; j < numFilesOfThisType; j++) {
          String filename = Resource.generateFilename(currentFile) + "." + typeName;
          names[currentFile] = filename;
          currentFile++;
        }
      }

      // GUID BLOCK
      // 4 - Header (GUID)
      // 12 - null
      fm.skip(16);

      // SCHM BLOCK
      // 4 - Header (SCHM)
      // 4 - Unknown (1)
      // 8 - null
      fm.skip(16);

      // 4 - Header (STRS)
      fm.skip(4);

      // 4 - Number of Strings
      int numStrings = fm.readInt();
      FieldValidator.checkPositive(numStrings);

      // 4 - String Data Length (not including these fields or the offsets)
      int stringsLength = fm.readInt();
      FieldValidator.checkLength(stringsLength, arcSize);

      // skip the offsets
      fm.skip(numStrings * 4);

      long stringsOffset = fm.getOffset();

      // add the Strings table as the first file
      resources[0] = new Resource(path, names[0], stringsOffset, stringsLength);

      fm.skip(stringsLength);

      // find the first CTNR header
      long remainingLength = fm.getRemainingLength();
      while (remainingLength > 0) {
        if (fm.readByte() == 67) {
          if (fm.readByte() == 84) {
            if (fm.readByte() == 78) {
              if (fm.readByte() == 82) {
                // found the file
                break;
              }
              else {
                fm.relativeSeek(fm.getOffset() - 3);
              }
            }
            else {
              fm.relativeSeek(fm.getOffset() - 2);
            }
          }
          else {
            fm.relativeSeek(fm.getOffset() - 1);
          }
        }
        remainingLength--;
      }

      // Loop through directory
      int realNumFiles = 1;
      for (int i = 1; i < numFiles; i++) { // starting at file #1 instead of #0 (which is the strings table)
        long offset = fm.getOffset();

        long length = 0;
        if (i == numFiles - 1) {
          // this file runs to the end of the archive
          length = arcSize - offset;
        }
        else {
          if (offset >= arcSize) {
            break; // premature EOF
          }

          // find the CTNR header for the **NEXT** file
          remainingLength = fm.getRemainingLength();
          while (remainingLength > 0) {
            if (fm.readByte() == 67) {
              if (fm.readByte() == 84) {
                if (fm.readByte() == 78) {
                  if (fm.readByte() == 82) {
                    // found the file
                    break;
                  }
                  else {
                    fm.relativeSeek(fm.getOffset() - 3);
                  }
                }
                else {
                  fm.relativeSeek(fm.getOffset() - 2);
                }
              }
              else {
                fm.relativeSeek(fm.getOffset() - 1);
              }
            }
            remainingLength--;
          }

          length = (fm.getOffset() - 4) - offset; // -4 because we need the offset of the point BEFORE the CTNR header to calculate the length
        }

        String filename = names[i];

        filename = Resource.generateFilename(realNumFiles);

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length);
        realNumFiles++;

        TaskProgressManager.setValue(i);
      }

      if (realNumFiles < numFiles) {
        resources = resizeResources(resources, realNumFiles);
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
