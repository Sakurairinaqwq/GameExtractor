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

import org.watto.ErrorLogger;
import org.watto.Language;
import org.watto.datatype.Archive;
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
public class Plugin_DATTEXARC extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_DATTEXARC() {

    super("DATTEXARC", "DATTEXARC");

    //         read write replace rename
    setProperties(true, true, false, false);

    setGames("The Great Escape");
    setExtensions("dat_texarc"); // MUST BE LOWER CASE
    setPlatforms("PS2");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("dattexarc_tex", "Texture Image", FileType.TYPE_IMAGE));

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

      fm.skip(8);

      int imageFormat = fm.readInt();
      if (imageFormat == 4 || imageFormat == 8) {
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

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      // Loop through directory
      int realNumFiles = 0;
      while (fm.getOffset() < arcSize) {
        //System.out.println(fm.getOffset());
        long offset = fm.getOffset();

        // 4 - Unknown
        // 4 - Unknown
        fm.skip(8);

        // 4 - Image Format? (8=8bit Paletted, 4=4bit Paletted)
        int imageFormat = fm.readInt();

        // 4 - Image Width
        int width = fm.readInt();
        FieldValidator.checkWidth(width);

        // 4 - Image Height
        int height = fm.readInt();
        FieldValidator.checkHeight(height);

        // 2 - Unknown (1)
        fm.skip(2);

        // 2 - Number of Mipmaps [+1]
        int numMipmaps = fm.readShort() + 1;
        FieldValidator.checkRange(numMipmaps, 0, 12);//guess
        // 4 - Flags
        // 4 - Unknown (64/4)
        // 4 - Unknown (1)
        fm.skip(12);

        int length = 36;
        if (imageFormat == 4) {
          length += 64; // color palette
          length += width * height / 2; // pixels

          for (int m = 1; m < numMipmaps; m++) {
            width /= 2;
            height /= 2;

            length += width * height / 2; // pixels
          }

        }
        else if (imageFormat == 8) {
          length += 1024; // color palette
          length += width * height; // pixels

          for (int m = 1; m < numMipmaps; m++) {
            width /= 2;
            height /= 2;

            length += width * height / 2; // pixels
          }
        }
        else {
          ErrorLogger.log("[DATTEXARC] Unknown Image Format: " + imageFormat);
          return null;
        }

        fm.seek(offset + length);

        String filename = Resource.generateFilename(realNumFiles) + ".dattexarc_tex";

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length);
        realNumFiles++;

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
   * Writes an [archive] File with the contents of the Resources
   **********************************************************************************************
   **/
  @Override
  public void write(Resource[] resources, File path) {
    try {

      FileManipulator fm = new FileManipulator(path, true);
      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      // Write Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      write(resources, fm);

      //ExporterPlugin exporter = new Exporter_ZLib();
      //long[] compressedLengths = write(exporter,resources,fm);

      fm.close();

    }
    catch (Throwable t) {
      logError(t);
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
