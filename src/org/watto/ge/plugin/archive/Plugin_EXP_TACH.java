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
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.exporter.Exporter_Deflate_CompressedSizeOnly;
import org.watto.io.FileManipulator;
import org.watto.io.FilenameSplitter;
import org.watto.io.converter.IntConverter;
import org.watto.io.converter.ShortConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_EXP_TACH extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_EXP_TACH() {

    super("EXP_TACH", "EXP_TACH");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Skydrift", "Skydrift Infinity");
    setExtensions("exp"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    setTextPreviewExtensions("met"); // LOWER CASE

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
      if (fm.readString(4).equals("TACH")) {
        rating += 50;
      }

      if (fm.readShort() == 1) {
        rating += 5;
      }
      if (fm.readShort() == 3) {
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

      // 4 - Header (TACH)
      // 2 - Unknown (1)
      // 2 - Unknown (3)
      fm.skip(8);

      // the whole file is compressed using Deflate
      FileManipulator decompFM = decompressArchive(fm);
      if (decompFM != null) {
        fm.close(); // close the original archive
        fm = decompFM; // now we're going to read from the decompressed file instead
        fm.seek(0); // go to the same point in the decompressed file as in the compressed file

        path = fm.getFile(); // So the resources are stored against the decompressed file
        arcSize = fm.getLength();
      }
      else {
        return null;
      }

      fm.getBuffer().setBufferSize(512); // small quick reads

      // 4 - Number of Files? [+1]
      fm.seek(4);

      // for each file
      int numFiles = Archive.getMaxFiles();
      int realNumFiles = 0;

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      // Loop through directory
      while (fm.getOffset() < arcSize) {

        // (optional) 4 - null
        // 4 - File Header Length (not including these 2 header fields)
        int headerLength = IntConverter.changeFormat(fm.readInt());
        if (headerLength == 0) {
          headerLength = IntConverter.changeFormat(fm.readInt());
        }
        FieldValidator.checkLength(headerLength, arcSize);

        long currentOffset = fm.getOffset();

        // X - File Header

        // try to find the filename
        int stringID = -1;
        int nameID = -1;
        String filename = null;

        try {
          int remainingHeaderLength = headerLength;
          while (remainingHeaderLength > 0) {

            // 4 - Type Name Length
            int typeNameLength = IntConverter.changeFormat(fm.readInt());
            FieldValidator.checkRange(typeNameLength, 0, 512); // guess
            remainingHeaderLength -= 4;

            while (remainingHeaderLength > 0 && typeNameLength != 0) {
              // X - Type Name
              String typeName = fm.readString(typeNameLength);
              remainingHeaderLength -= typeNameLength;

              // 2 - Type Name ID
              short typeNameID = ShortConverter.changeFormat(fm.readShort());
              remainingHeaderLength -= 2;

              if (typeName.equals("String")) {
                stringID = typeNameID;
              }

              // 4 - Type Name Length (of the next type)
              typeNameLength = IntConverter.changeFormat(fm.readInt());
              FieldValidator.checkRange(typeNameLength, 0, 512); // guess
              remainingHeaderLength -= 4;
            }

            if (stringID == -1) {
              break; // "String" not found
            }

            // 4 - Primary Type Name Length
            typeNameLength = IntConverter.changeFormat(fm.readInt());
            FieldValidator.checkRange(typeNameLength, 0, 512); // guess
            remainingHeaderLength -= 4;

            // X - Primary Type Name
            fm.skip(typeNameLength);
            remainingHeaderLength -= typeNameLength;

            // 2 - Primary Type Name ID
            fm.skip(2);
            remainingHeaderLength -= 2;

            // 4 - Object Name Length
            int objectNameLength = IntConverter.changeFormat(fm.readInt());
            FieldValidator.checkRange(objectNameLength, 0, 512); // guess
            remainingHeaderLength -= 4;

            int objectCount = 0;
            boolean[] stringObjects = new boolean[50]; // guess max 50 objects in the metadata
            while (remainingHeaderLength > 0 && objectNameLength != 0) {
              // X - Object Name
              String objectName = fm.readString(objectNameLength);
              remainingHeaderLength -= objectNameLength;

              // 2 - Type Name ID
              short objectTypeID = ShortConverter.changeFormat(fm.readShort());
              remainingHeaderLength -= 2;

              if (objectTypeID == stringID && objectName.equals("Name")) {
                nameID = objectCount;
              }

              stringObjects[objectCount] = (objectTypeID == stringID);

              objectCount++;

              if (objectCount >= 50) {
                // array too small
                ErrorLogger.log("[EXP_TACH] More than 50 Objects in the metadata");
                break;
              }

              // 4 - Object Name Length (of the next object)
              objectNameLength = IntConverter.changeFormat(fm.readInt());
              FieldValidator.checkRange(objectNameLength, 0, 512); // guess
              remainingHeaderLength -= 4;
            }

            if (nameID == -1) {
              break; // "Name" not found
            }

            // Now we know what Object has Name="Name" and Type="String", so just go through and find the name

            // 4 - Unknown
            if (fm.readInt() != 0) {
              break; // for some reason, doesn't actually contain the name
            }
            remainingHeaderLength -= 4;

            // 4 - Unknown
            // 2 - Class Name ID
            fm.skip(6);
            remainingHeaderLength -= 6;

            int objectsRead = 0;
            while (remainingHeaderLength > 0 && objectsRead < objectCount) {
              if (stringObjects[objectsRead]) {
                // 4 - String Length
                int nameLength = IntConverter.changeFormat(fm.readInt());
                FieldValidator.checkRange(nameLength, 0, 512); // guess
                remainingHeaderLength -= 4;

                // X - String
                if (objectsRead == nameID) {
                  // this one is the filename
                  filename = fm.readString(nameLength);
                  remainingHeaderLength = 0; // force the exit of the larger loop

                  break;
                }
                else {
                  // some other string - skip
                  fm.skip(nameLength);
                  remainingHeaderLength -= nameLength;
                }

              }
              else {
                // 4 - Unknown
                fm.skip(4);
                remainingHeaderLength -= 4;
              }
            }

          }
        }
        catch (Throwable t) {
          // any errors, just skip the filename finding for this file
        }

        fm.relativeSeek(currentOffset + headerLength);

        // 4 - File Length
        int length = IntConverter.changeFormat(fm.readInt());
        FieldValidator.checkLength(length, arcSize);

        // X - File Data
        long offset = fm.getOffset();
        fm.skip(length);

        if (filename == null) {
          filename = Resource.generateFilename(realNumFiles);
        }

        //path,name,offset,length,decompLength,exporter
        Resource resource = new Resource(path, filename, offset, length);
        resource.forceNotAdded(true);
        resources[realNumFiles] = resource;
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
   Decompressed an archive, where the whole archive is compressed.
   Reads the compressed block information first, then processes the compressed blocks themselves.
   Writes the output to a file with the same name, but with "_ge_decompressed" at the end of it.
   The decompressed file contains the same header as the compressed file, so you can open
   the decompressed file in GE directly, without needing to re-decompress anything.
   If the decompressed file already exists, we use that, we don't re-decompress.
   **********************************************************************************************
   **/
  public FileManipulator decompressArchive(FileManipulator fm) {
    try {
      // Build a new "_ge_decompressed" archive file in the current directory
      File origFile = fm.getFile();

      String pathOnly = FilenameSplitter.getDirectory(origFile);
      String filenameOnly = FilenameSplitter.getFilename(origFile);
      String extensionOnly = FilenameSplitter.getExtension(origFile);

      File decompFile = new File(pathOnly + File.separatorChar + filenameOnly + "_ge_decompressed" + "." + extensionOnly);
      if (decompFile.exists()) {
        // we've already decompressed this file before - open and return it
        return new FileManipulator(decompFile, false);
      }

      FileManipulator decompFM = new FileManipulator(decompFile, true);

      int compLength = (int) (fm.getLength() - fm.getOffset());

      // Now decompress the block into the decompressed file
      TaskProgressManager.setMessage(Language.get("Progress_DecompressingArchive")); // progress bar
      TaskProgressManager.setMaximum(compLength); // progress bar
      TaskProgressManager.setIndeterminate(true);

      Exporter_Deflate_CompressedSizeOnly exporter = Exporter_Deflate_CompressedSizeOnly.getInstance();
      exporter.open(fm, compLength, compLength);

      while (exporter.available()) {
        decompFM.writeByte(exporter.read());
      }

      // Force-write out the decompressed file to write it to disk, then change the buffer to read-only.
      decompFM.close();
      decompFM = new FileManipulator(decompFile, false);

      TaskProgressManager.setMessage(Language.get("Progress_ReadingArchive")); // progress bar
      TaskProgressManager.setIndeterminate(false);

      // Return the file pointer to the beginning, and return the decompressed file
      decompFM.seek(0);
      return decompFM;
    }
    catch (Throwable t) {
      ErrorLogger.log(t);
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
