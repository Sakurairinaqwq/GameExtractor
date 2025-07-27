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
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_DAT_PAK extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_DAT_PAK() {

    super("DAT_PAK", "DAT_PAK");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Scooby-Doo! and the Spooky Swamp",
        "Scooby-Doo! First Frights");
    setExtensions("dat"); // MUST BE LOWER CASE
    setPlatforms("PS2", "PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("script", "Script", FileType.TYPE_DOCUMENT),
        new FileType("hnk", "Hunk Archive", FileType.TYPE_ARCHIVE));

    setTextPreviewExtensions("script"); // LOWER CASE

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
      if (fm.readString(3).equals("PAK")) {
        rating += 40;
      }
      if (fm.readByte() == 0) {
        rating += 10;
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

      Exporter_ZLib exporter = Exporter_ZLib.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 4 - Header ("PAK" + null)
      fm.skip(4);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {
        // 4 - Hash
        fm.skip(4);

        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        String filename = Resource.generateFilename(i);

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length);

        TaskProgressManager.setValue(i);
      }

      // work out if the files are compressed or not
      fm.getBuffer().setBufferSize(8);
      fm.seek(1);

      for (int i = 0; i < numFiles; i++) {
        TaskProgressManager.setValue(i);

        Resource resource = resources[i];

        fm.seek(resource.getOffset());

        // 4 - Compression Header (!ZLS)
        String header = fm.readString(4);
        if (header.equals("!ZLS")) {

          // 4 - Decompressed Length
          int decompLength = fm.readInt();
          FieldValidator.checkLength(decompLength);

          // X - File Data (ZLib Compression)
          long offset = fm.getOffset();

          long length = resource.getLength() - 8;

          resource.setLength(length);
          resource.setDecompressedLength(decompLength);
          resource.setOffset(offset);
          resource.setExporter(exporter);

          // We no longer do this here, as the filenames and types aren't accurate.
          // Just leave it as an Object, and process it by the Plugin_OBJECT instead.
          /*
          
          // Extract the first little bit of the file, to try and work out the filename and the directory names
          int extractLength = (int) length;
          if (extractLength > 1024) {
            extractLength = 1024;
          }
          
          int extractDecompLength = (int) decompLength;
          if (extractDecompLength > 1024) {
            extractDecompLength = 1024;
          }
          
          byte[] compBytes = fm.readBytes(extractLength);
          FileManipulator compFM = new FileManipulator(new ByteBuffer(compBytes));
          
          byte[] decompBytes = new byte[extractDecompLength];
          exporter.open(compFM, extractLength, extractDecompLength);
          
          int decompWritePos = 0;
          for (int b = 0; b < extractDecompLength; b++) {
            if (exporter.available()) { // make sure we read the next bit of data, if required
              decompBytes[decompWritePos++] = (byte) exporter.read();
            }
          }
          
          // open the decompressed data for processing
          compFM.close();
          compFM = new FileManipulator(new ByteBuffer(decompBytes));
          
          // check the header
          String filename = "";
          short shortHeader = compFM.readShort();
          if (shortHeader == 592) {
            // Object
          
            try {
              // 4 - Unknown (592) // we already read 2 bytes above
              // 2 - Unknown (112)
              // 2 - Unknown (4)
              // 4 - Unknown
              // 2 - Unknown (1)
              // 2 - Unknown (2/4)
              // 4 - Flags?
              compFM.skip(18);
          
              // 4 - Number of Directory Names
              int numDirNames = compFM.readInt();
              FieldValidator.checkRange(numDirNames, 1, 10); // guess
          
              for (int n = 0; n < numDirNames; n++) {
                // 64 - Directory Name (null terminated, filled with nulls)
                String dirName = compFM.readNullString(64);
                FieldValidator.checkFilename(dirName);
                filename += dirName + "\\";
          
                // 4 - Unknown
                // 4 - Unknown
                compFM.skip(8);
              }
          
              // X - null Padding to offset 600
              compFM.seek(600);
          
              // 4 - Unknown (36)
              // 2 - Unknown (113)
              // 2 - Unknown (4)
              // 2 - Unknown (1)
              // 2 - Unknown (4)
              // 2 - Number of Names (2)
              // for each name
              //   2 - Name Length (including null terminator)
              compFM.skip(18);
          
              // The first name is the object type
              String type = compFM.readNullString();
              // The second name is the filename
              filename += compFM.readNullString() + "." + type;
          
            }
            catch (Throwable t) {
              // don't worry, just set the filename
              filename = resource.getName() + ".Object"; // either "graphics" or "sound", for example
            }
          
          }
          else if (shortHeader == 24941) {
            // Script
            filename = resource.getName() + ".Script";
          }
          else {
            filename = resource.getName() + "." + shortHeader;
          }
          
          resource.setName(filename);
          resource.setOriginalName(filename);
          */

        }
        else if (header.equals("ALPO")) {
          // do nothing - only 1 of these files, not sure what it is
        }
        else {
          // all other files have a 16-byte null header
          long length = resource.getLength() - 16;
          resource.setLength(length);
          resource.setDecompressedLength(length);

          long offset = resource.getOffset() + 16;
          resource.setOffset(offset);

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

    if (headerShort1 == 592) {
      return "hnk"; // either "graphics" or "sound", for example
    }
    else if (headerShort1 == 24941) {
      return "Script";
    }
    else if (headerShort1 == 19521) {
      return "ALPO";
    }
    else {
      return "" + headerShort1;
    }

  }

}
