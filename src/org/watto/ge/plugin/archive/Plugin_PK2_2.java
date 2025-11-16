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
import org.watto.datatype.Archive;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_PK2_2 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_PK2_2() {

    super("PK2_2", "PK2_2");

    //         read write replace rename
    setProperties(true, true, false, true);

    setGames("Indiana Jones and the Emperors Tomb");
    setExtensions("pk2");
    setPlatforms("PS2");

    setTextPreviewExtensions("str"); // LOWER CASE

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

      getDirectoryFile(fm.getFile(), "hsh");
      rating += 25;

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

      // RESETTING THE GLOBAL VARIABLES

      long arcSize = (int) path.length();

      File sourcePath = getDirectoryFile(path, "hsh");

      FileManipulator fm = new FileManipulator(sourcePath, false);

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];

      TaskProgressManager.setMaximum(arcSize);

      // Loop through directory
      int realNumFiles = 0;
      while (fm.getOffset() < fm.getLength()) {
        // X - Filename (String)
        // 1 - Filename Terminator (byte 32)
        // X - File Length (String)
        // 1 - File Length Terminator (byte 32)
        // X - File Offset (String)
        // 2 - New Line Characters (13,10)

        String line = fm.readLine();

        long offsetStartPos = line.lastIndexOf(' ');
        long offset = Long.parseLong(line.substring((int) (offsetStartPos + 1)));
        FieldValidator.checkOffset(offset, arcSize);

        line = line.substring(0, (int) offsetStartPos);

        long lengthStartPos = line.lastIndexOf(' ');
        long length = Long.parseLong(line.substring((int) (lengthStartPos + 1)));
        FieldValidator.checkLength(length, arcSize);

        String filename = line.substring(0, (int) lengthStartPos);

        //System.out.println(filename + " - " + length + " - " + offset);

        //path,id,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length);

        TaskProgressManager.setValue(realNumFiles);
        realNumFiles++;
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

      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      // WRITE THE FILE THAT CONTAINS THE DIRECTORY
      File dirPath = getDirectoryFile(path, "hsh", false);
      FileManipulator fm = new FileManipulator(dirPath, true);

      // Write Directory
      TaskProgressManager.setMessage(Language.get("Progress_WritingDirectory"));
      long offset = 0;
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        long decompLength = resource.getDecompressedLength();

        // X - Filename (String)
        fm.writeString(resource.getName());

        // 1 - Filename Terminator (byte 32)
        fm.writeByte(32);

        // X - File Length (String)
        fm.writeString("" + decompLength);

        // 1 - File Length Terminator (byte 32)
        fm.writeByte(32);

        // X - File Offset (String)
        fm.writeString("" + offset);

        // 2 - New Line Characters (13,10)
        fm.writeByte(13);
        fm.writeByte(10);

        offset += decompLength;
        offset += calculatePadding(decompLength, 2048);
      }

      fm.close();

      // WRITE THE FILE THAT CONTAINS THE DATA
      fm = new FileManipulator(path, true);

      // Write Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];

        // X - File Data
        write(resource, fm);

        // 0-2047 - null Padding to a multiple of 2048 bytes
        int padding = calculatePadding(resource.getDecompressedLength(), 2048);
        for (int p = 0; p < padding; p++) {
          fm.writeByte(0);
        }

        TaskProgressManager.setValue(i);
      }

      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}
