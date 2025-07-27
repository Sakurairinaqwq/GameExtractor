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

import org.watto.Language;
import org.watto.Settings;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_IMG_4 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_IMG_4() {

    super("IMG_4", "IMG_4");

    //         read write replace rename
    setProperties(true, false, true, false);

    setGames("Grand Theft Auto: Liberty City Stories");
    setExtensions("img"); // MUST BE LOWER CASE
    setPlatforms("PSP");

    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

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

      getDirectoryFile(fm.getFile(), "DIR");
      rating += 25;

      // Header
      if (fm.readString(4).equals("ANPK")) {
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

      long arcSize = (int) path.length();

      File sourcePath = getDirectoryFile(path, "DIR");
      FileManipulator fm = new FileManipulator(sourcePath, false);

      int numFiles = ((int) fm.getLength()) / 32;
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {

        // 4 - File Offset
        long offset = fm.readInt() * 2048;
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length
        long length = fm.readInt() * 2048;
        FieldValidator.checkLength(length, arcSize);

        // 24 - Filename (null terminated, filled with junk)
        String filename = fm.readNullString(24);
        FieldValidator.checkFilename(filename);

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length);

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
   * Writes an [archive] File with the contents of the Resources. The archive is written using
   * data from the initial archive - it isn't written from scratch.
   **********************************************************************************************
   **/
  @Override
  public void replace(Resource[] resources, File path) {
    try {

      // Work out the filenames for the Data and Directory files
      File sourceDataFile = new File(Settings.getString("CurrentArchive"));
      File sourceDirFile = getDirectoryFile(sourceDataFile, "DIR", true);

      File targetDataFile = path;
      File targetDirFile = getDirectoryFile(targetDataFile, "DIR", false);

      // 
      // WRITE THE FILE THAT CONTAINS THE DIRECTORY
      //
      FileManipulator src = new FileManipulator(sourceDirFile, false);
      FileManipulator fm = new FileManipulator(targetDirFile, true);

      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      // Write Directory
      TaskProgressManager.setMessage(Language.get("Progress_WritingDirectory"));
      long offset = 0;
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        long length = resource.getDecompressedLength();

        // padded out to 2048 bytes
        int paddingSize = calculatePadding(length, 2048);
        length += paddingSize;

        // 4 - File Offset
        fm.writeInt(offset / 2048);
        src.skip(4);

        // 4 - File Length
        fm.writeInt(length / 2048);
        src.skip(4);

        // 24 - Filename (null terminated, filled with junk)
        fm.writeBytes(src.readBytes(24));

        offset += length;
      }

      src.close();
      fm.close();

      //
      // WRITE THE FILE THAT CONTAINS THE DATA
      //
      fm = new FileManipulator(targetDataFile, true);

      // Write Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      for (int i = 0; i < resources.length; i++) {
        Resource resource = resources[i];

        // X - File Data
        write(resource, fm);

        // X - null Padding to a multiple of 2048 bytes
        long length = resource.getDecompressedLength();
        int paddingSize = calculatePadding(length, 2048);

        for (int p = 0; p < paddingSize; p++) {
          fm.writeByte(0);
        }

        TaskProgressManager.setValue(i);
      }

      //ExporterPlugin exporter = Exporter_ZLib.getInstance();
      //long[] compressedLengths = write(exporter,resources,fm);

      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}
