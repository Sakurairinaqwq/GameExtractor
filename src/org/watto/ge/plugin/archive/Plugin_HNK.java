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
public class Plugin_HNK extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_HNK() {

    super("HNK", "HNK");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Scooby-Doo! First Frights");
    setExtensions("hnk"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("rendertextureset", "Texture Image", FileType.TYPE_IMAGE),
        new FileType("rendermodeltemplate", "Model Mesh", FileType.TYPE_MODEL));

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

      if (fm.readShort() == 592) {
        rating += 25;
      }

      fm.skip(14);

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

      // 4 - Header Length (592)
      // 2 - Unknown (112)
      // 2 - Unknown (4)
      // 4 - Unknown
      // 2 - Unknown (1)
      // 2 - Unknown (2/4)
      // 4 - Number of Files in this Archive?

      // DIRECTORY NAMES
      // 4 - Number of Directory Names

      // for each directory name
      //     64 - Directory Name (null terminated, filled with nulls)
      //     4 - Unknown
      //     4 - Unknown

      // X - null Padding to offset 592

      // 8 - null
      fm.seek(600);

      int numFiles = Archive.getMaxFiles();
      int realNumFiles = 0;

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      while (fm.getOffset() < arcSize) {
        long offset = fm.getOffset();

        // 4 - Unknown
        // 2 - Unknown (113)
        // 2 - Unknown (4)
        // 2 - Unknown (1)
        // 2 - Unknown (4)
        // 2 - Unknown (2/3)
        // 2 - File Type Length (including null terminator)
        // 2 - File Name Length (including null terminator)
        fm.skip(18);

        // X - File Type
        // 1 - null File Type Terminator
        String type = fm.readNullString();
        FieldValidator.checkFilename(type);

        // X - File Name
        // 1 - null File Name Terminator
        String name = fm.readNullString();
        FieldValidator.checkFilename(type);

        String filename = name + "." + type;

        //System.out.println("Found " + type + " at offset " + offset);

        if (type.equals("RenderTextureSet")) {
          // RenderTextureSet

          // 4 - Unknown (96)
          // 4 - Unknown (266326)
          // 4 - Unknown (1)
          // 4 - Unknown (1)
          // 4 - null
          // 4 - Unknown (16)
          // 4 - null
          // 4 - Unknown (2090944)
          // 4 - Unknown (1)
          // 4 - Unknown (1)
          // 4 - Unknown (2)
          // 4 - Unknown (2)
          // 4 - Unknown (2)
          // 4 - Unknown (1)
          // 2 - Image Width
          // 2 - Image Height
          // 2 - Unknown (1)
          // 2 - Unknown (6)
          // 4 - Image Format ("DXT1", "DXT5")
          // 4 - Unknown (3)
          // 32 - Image Name (null terminated, filled with nulls)
          // 4 - Unknown (8)
          // 2 - Unknown (2)
          // 2 - Unknown (4)
          // 2 - Unknown (1)
          // 2 - Unknown (256)
          // 4 - Unknown (12)
          fm.skip(120);

          // 4 - Image Data Length
          long length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          // 2 - Unknown (87)
          // 2 - Unknown (4)
          fm.skip(4);

          // X - Image Data
          fm.skip(length);

          // 4 - null
          // 2 - Unknown (114)
          // 2 - Unknown (4)
          fm.skip(8);

          length = fm.getOffset() - offset;

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
          realNumFiles++;

          TaskProgressManager.setValue(offset);
        }
        else if (type.equals("RenderModelTemplate")) {
          // RenderModelTemplate

          // Don't know enough to read this file properly, so just read until we find the end marker

          // 4 - null
          // 2 - Unknown (114)
          // 2 - Unknown (4)
          while (fm.getOffset() < arcSize) {
            if (fm.readByte() == 114 && fm.readByte() == 0 && fm.readByte() == 4 && fm.readByte() == 0) {
              // check the 4 bytes before it were null

              fm.seek(fm.getOffset() - 8);
              if (fm.readInt() == 0) {
                // found it
                //System.out.println("Found end of RenderModelTemplate at " + (fm.getOffset() - 4));
                fm.skip(4); // skip the last 4 bytes of the footer
                break;
              }
              else {
                fm.skip(4); // didn't find it, start again 
              }
            }

          }

          long length = fm.getOffset() - offset;

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
          realNumFiles++;

          TaskProgressManager.setValue(offset);

        }
        else if (type.equals("EntityPlacement")) {
          // EntityPlacement

          // 4 - Data Length
          long length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          // 4 - Unknown
          fm.skip(4);
          // 4 - Unknown (3)
          int numBlocks = fm.readInt();
          //FieldValidator.checkRange(numBlocks, 0, 50);// guess

          fm.skip(numBlocks * 4);

          // 4 - Unknown
          // 4 - Unknown
          // 4 - Unknown
          // 4 - Unknown
          // 20 - null
          // 4 - Unknown
          fm.skip(40);

          // X - File Data
          fm.skip(length);

          // 4 - null
          // 2 - Unknown (114)
          // 2 - Unknown (4)
          fm.skip(8);

          // now we're part way through the file, lets continue reading to the end, as we're not 100% on this format yet

          // 4 - null
          // 2 - Unknown (114)
          // 2 - Unknown (4)
          while (fm.getOffset() < arcSize) {
            if (fm.readByte() == 114 && fm.readByte() == 0 && fm.readByte() == 4 && fm.readByte() == 0) {
              // check the 4 bytes before it were null

              fm.seek(fm.getOffset() - 8);
              if (fm.readInt() == 0) {
                // found it
                //System.out.println("Found end of EntityPlacement at " + (fm.getOffset() - 4));
                fm.skip(4); // skip the last 4 bytes of the footer
                break;
              }
              else {
                fm.skip(4); // didn't find it, start again 
              }
            }

          }

          length = fm.getOffset() - offset;

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
          realNumFiles++;

          TaskProgressManager.setValue(offset);
        }
        else if (type.equals("SqueakSample")) {
          // EntityPlacement

          // 4 - Data Length
          long length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          // 4 - Unknown
          // 4 - Unknown (32)
          // 4 - Unknown (1)
          // 4 - Unknown
          // 4 - Unknown
          // 4 - null
          fm.skip(24);

          // 4 - Count
          int count = fm.readInt() & 255;
          fm.skip(count * 4);

          // X - File Data
          fm.skip(length);

          // 4 - null
          // 2 - Unknown (114)
          // 2 - Unknown (4)
          fm.skip(8);

          // now we're part way through the file, lets continue reading to the end, as we're not 100% on this format yet

          // 4 - null
          // 2 - Unknown (114)
          // 2 - Unknown (4)
          while (fm.getOffset() < arcSize) {
            if (fm.readByte() == 114 && fm.readByte() == 0 && fm.readByte() == 4 && fm.readByte() == 0) {
              // check the 4 bytes before it were null

              fm.seek(fm.getOffset() - 8);
              if (fm.readInt() == 0) {
                // found it
                //System.out.println("Found end of EntityPlacement at " + (fm.getOffset() - 4));
                fm.skip(4); // skip the last 4 bytes of the footer
                break;
              }
              else {
                fm.skip(4); // didn't find it, start again 
              }
            }

          }

          length = fm.getOffset() - offset;

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
          realNumFiles++;

          TaskProgressManager.setValue(offset);
        }
        else {
          //ErrorLogger.log("[RENDERTEXTURESET] Unknown Entry Type: " + type);

          // just read until we find the end marker

          // 4 - null
          // 2 - Unknown (114)
          // 2 - Unknown (4)
          while (fm.getOffset() < arcSize) {
            if (fm.readByte() == 114 && fm.readByte() == 0 && fm.readByte() == 4 && fm.readByte() == 0) {
              // check the 4 bytes before it were null

              fm.seek(fm.getOffset() - 8);
              if (fm.readInt() == 0) {
                // found it
                //System.out.println("Found end of " + type + " at " + (fm.getOffset() - 4));
                fm.skip(4); // skip the last 4 bytes of the footer
                break;
              }
              else {
                fm.skip(4); // didn't find it, start again 
              }
            }

          }

          long length = fm.getOffset() - offset;

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
          realNumFiles++;

          TaskProgressManager.setValue(offset);

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

    /*
    if (headerInt1 == 2037149520) {
      return "js";
    }
    */

    return null;
  }

}
