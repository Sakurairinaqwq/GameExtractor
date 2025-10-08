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
public class Plugin_DBS extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_DBS() {

    super("DBS", "DBS");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Dino Crisis 2");
    setExtensions("dbs", "dat"); // MUST BE LOWER CASE
    setPlatforms("PC", "PSX");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("wav_arc", "Audio Archive", FileType.TYPE_ARCHIVE));

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

      // 4 - File Type ID?
      if (FieldValidator.checkRange(fm.readInt(), 0, 20)) { // guess max 20
        rating += 5;
      }

      long arcSize = fm.getLength();

      // 4 - Part Length (not including padding)
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
        rating += 5;
      }

      fm.skip(28);

      // 4 - Part Length (not including padding)
      int nextPartLength = fm.readInt();
      if (nextPartLength == 1701322873) {
        rating += 25; // found a dummy entry
        return rating;
      }
      else if (FieldValidator.checkLength(nextPartLength, arcSize)) {
        rating += 5;
      }

      fm.skip(28);

      // 4 - Part Length (not including padding)
      nextPartLength = fm.readInt();
      if (nextPartLength == 1701322873) {
        rating += 25; // found a dummy entry
        return rating;
      }
      else if (FieldValidator.checkLength(nextPartLength, arcSize)) {
        rating += 5;
      }

      fm.skip(28);

      // 4 - Part Length (not including padding)
      nextPartLength = fm.readInt();
      if (nextPartLength == 1701322873) {
        rating += 25; // found a dummy entry
        return rating;
      }
      else if (FieldValidator.checkLength(nextPartLength, arcSize)) {
        //rating += 5;
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
      long offset = 0;
      while (fm.getOffset() < arcSize) {
        //System.out.println(fm.getOffset());
        offset += 2048;

        boolean psxFunnyCheck = false;

        long startOffset = fm.getOffset();
        long endOffset = startOffset + 2048;
        while (fm.getOffset() < endOffset) {
          // 4 - File Type ID?
          int typeID = fm.readInt();
          if (typeID == 1835890020) {
            // found a dummy entry - end of directory
            fm.seek(offset); // get ready for the next iteration of the big loop
            break;
          }

          // 4 - Part Length (not including padding)
          int length = fm.readInt();
          try {
            FieldValidator.checkLength(length, arcSize);
          }
          catch (Throwable t) {
            // PSX files contain random 2048-byte parts at the end of each file - need to detect and correct here
            if (psxFunnyCheck) {
              // 2 funny checks mean the file is something else, not from this game.
              return null;
            }

            psxFunnyCheck = true;
            fm.seek(offset);
            break;
          }

          if (typeID == 0 && length == 0) {
            // a blank 2048 byte file
            fm.seek(offset);
            break;
          }

          // 2 - Unknown
          // 4 - Unknown
          // 4 - Unknown
          // 14 - null
          fm.skip(24);

          String filename = Resource.generateFilename(realNumFiles);

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
          realNumFiles++;

          TaskProgressManager.setValue(offset);

          offset += length;
          offset += calculatePadding(offset, 2048);
        }
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

    if (headerShort1 == -1025) {
      return "mp3";
    }
    else if (headerBytes[1] == 0 && headerBytes[3] == 64 && headerBytes[9] == 0 && headerBytes[11] == 64) {
      return "wav_arc";
    }

    return null;
  }

}
