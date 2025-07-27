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
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_TEX_6 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_TEX_6() {

    super("TEX_6", "TEX_6");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Hitman: Blood Money");
    setExtensions("tex"); // MUST BE LOWER CASE
    setPlatforms("PS2");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("data", "Texture Image", FileType.TYPE_IMAGE));

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

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Directory Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // Archive Size
      if (FieldValidator.checkEquals(fm.readInt(), arcSize)) {
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

      // 4 - Number of Files (including the pointer to the header)
      int numFiles = fm.readInt() - 1;
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Directory Offset
      int dirOffset = fm.readInt() + 16;
      FieldValidator.checkOffset(dirOffset, arcSize);

      // 4 - Archive Length
      // 4 - Number of Files (not including the pointer to the header)
      fm.seek(dirOffset);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      boolean[] headers = new boolean[numFiles];
      for (int i = 0; i < numFiles; i++) {
        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - Type? (1/22/15/76/...)
        // 4 - null
        fm.skip(8);

        String filename = Resource.generateFilename(i);

        if (length == 96) {
          headers[i] = true;
        }
        else {
          headers[i] = false;
        }

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length);

        TaskProgressManager.setValue(i);
      }

      // go through and check the headers, link them to the file data and palettes
      fm.getBuffer().setBufferSize(96);
      for (int i = 0; i < numFiles; i++) {
        if (headers[i]) {
          Resource headerResource = resources[i];
          fm.seek(headerResource.getOffset());

          try {

            // 2 - Image Width
            short width = fm.readShort();
            FieldValidator.checkWidth(width);

            // 2 - Image Height
            short height = fm.readShort();
            FieldValidator.checkHeight(height);

            // 12 - null
            // 4 - Unknown
            fm.skip(16);

            // 24 - Filename
            String filename = fm.readNullString(24);

            // 4 - Unknown
            // 4 - Unknown
            // 4 - Unknown
            // 4 - Image Data Length
            // 4 - Unknown
            // 4 - Unknown
            // 4 - Unknown
            fm.skip(28);

            // 4 - Palette File ID
            int paletteID = fm.readInt() - 1;
            if (paletteID != -1) { // palette=-1 should be allowed, it means RGBA data rather than paletted
              FieldValidator.checkRange(paletteID, 0, numFiles);
            }

            // 4 - Image Data File ID
            int dataID = fm.readInt() - 1;
            FieldValidator.checkRange(dataID, 0, numFiles);

            // 4 - Unknown
            // 4 - Unknown
            // 8 - null

            String headerFilename = filename + ".header";
            headerResource.setName(headerFilename);
            headerResource.setOriginalName(headerFilename);

            if (paletteID != -1) {
              Resource paletteResource = resources[paletteID];

              String paletteFilename = filename + ".palette";
              paletteResource.setName(paletteFilename);
              paletteResource.setOriginalName(paletteFilename);
            }

            Resource dataResource = resources[dataID];

            String dataFilename = filename + ".data";
            dataResource.setName(dataFilename);
            dataResource.setOriginalName(dataFilename);

            dataResource.addProperty("Width", width);
            dataResource.addProperty("Height", height);
            dataResource.addProperty("PaletteID", paletteID);

          }
          catch (Throwable t) {
            // maybe not really a header file, just leave it
          }

        }
        TaskProgressManager.setValue(i);
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
