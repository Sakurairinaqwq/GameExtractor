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

import org.watto.datatype.Palette;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.helper.PaletteManager;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;
import org.watto.io.converter.ShortConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_SPR_SPRITEFILE extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_SPR_SPRITEFILE() {

    super("SPR_SPRITEFILE", "SPR_SPRITEFILE");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Blackwell Unbound");
    setExtensions("spr"); // MUST BE LOWER CASE
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

      if (fm.readShort() == 4) {
        rating += 5;
      }

      // Header
      if (fm.readString(13).equals(" Sprite File ")) {
        rating += 50;
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

      // 2 - Version (4)
      // 13 - Header (" Sprite File ")
      fm.skip(15);

      // COLOR PALETTE
      int[] palette = ImageFormatReader.readPaletteRGB(fm, 256);
      PaletteManager.clear();
      PaletteManager.addPalette(new Palette(palette));

      // 2 - Number of Sprites
      int numFiles = ShortConverter.unsign(fm.readShort());
      FieldValidator.checkNumFiles(numFiles);

      fm.getBuffer().setBufferSize(6); // small quick reads
      fm.seek(1);
      fm.seek(785);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      int realNumFiles = 0;
      for (int i = 0; i < numFiles; i++) {

        long offset = fm.getOffset();
        if (offset >= arcSize) {
          realNumFiles = i;
          break;
        }
        //System.out.println(i + " of " + numFiles + " - " + offset);

        FieldValidator.checkOffset(offset, arcSize);

        // 1 - Coldep
        int coldep = ByteConverter.unsign(fm.readByte());

        // 1 - null
        fm.skip(1);

        // 2 - Image Width
        int width = ShortConverter.unsign(fm.readShort());

        // 2 - Image Height
        int height = ShortConverter.unsign(fm.readShort());

        FieldValidator.checkWidth(width + 1); // to allow width=0
        FieldValidator.checkHeight(height + 1); // to allow height=0

        // X - Sprite Data (size = coldep * w * h)
        int length = coldep * width * height;
        fm.skip(length);

        length += 6;

        String filename = Resource.generateFilename(i) + ".spr_tex";

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length);

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
