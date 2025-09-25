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
public class Plugin_GLB_GLB2 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_GLB_GLB2() {

    super("GLB_GLB2", "GLB_GLB2");

    //         read write replace rename
    setProperties(true, false, true, false);

    setExtensions("glb");
    setGames("Demon Star");
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("glb_tex", "Texture Image", FileType.TYPE_IMAGE));

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
      if (fm.readString(6).equals("GLB2.0")) {
        rating += 50;
      }

      fm.skip(2);

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
  
  **********************************************************************************************
  **/
  @Override
  public Resource[] read(File path) {
    try {

      addFileTypes();

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 8 - Header (GLB2.0) + null null
      fm.skip(8);

      // 4 - Number of Entries
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      int realNumFiles = 0;
      String dirName = "";
      for (int i = 0; i < numFiles; i++) {
        // 4 - Unknown (null)
        fm.skip(4);

        // 4 - File Offset
        long offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length
        long length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 16 - Filename
        String filename = fm.readNullString(16);
        FieldValidator.checkFilename(filename);

        if (offset == 0 || length == 0) {
          if (filename.startsWith("START")) {
            dirName = filename.substring(5, filename.length() - 1) + "\\";
          }
          else if (filename.startsWith("END")) {
            dirName = "";
          }
        }
        else {
          //path,id,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, dirName + filename, offset, length);

          TaskProgressManager.setValue(i);
          realNumFiles++;
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
  
  **********************************************************************************************
  **/
  @Override
  public void write(Resource[] resources, File path) {
    try {

      FileManipulator fm = new FileManipulator(path, true);
      FileManipulator src = new FileManipulator(new File(Settings.getString("CurrentArchive")), false);

      // 8 - Header (GLB2.0) + null null
      fm.writeBytes(src.readBytes(8));

      // 4 - Number of Files
      int srcNumFiles = src.readInt();
      fm.writeInt(srcNumFiles);

      TaskProgressManager.setMaximum(srcNumFiles);

      TaskProgressManager.setMessage(Language.get("Progress_WritingDirectory"));
      long offset = 12 + (28 * srcNumFiles);

      int currentFile = 0;
      for (int i = 0; i < srcNumFiles; i++) {
        // 4 - Unknown (null)
        fm.writeBytes(src.readBytes(4));

        // 4 - File Offset
        // 4 - File Length
        int srcOffset = src.readInt();
        int srcLength = src.readInt();

        if (srcOffset == 0 || srcLength == 0) {
          // dirstart/end
          fm.writeInt(srcOffset);

          // 4 - File Length
          fm.writeInt(srcLength);

          // 16 - Filename (null)
          fm.writeBytes(src.readBytes(16));
        }
        else {
          Resource resource = resources[currentFile];
          long length = resource.getDecompressedLength();

          // file
          fm.writeInt((int) offset);

          // 4 - File Length
          fm.writeInt((int) length);

          // 16 - Filename (null)
          fm.writeBytes(src.readBytes(16));
          currentFile++;

          offset += length;
        }

      }

      src.close();

      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      write(resources, fm);

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

    if (headerInt1 == 1179011410 && headerInt3 == 1396984141) {
      return "rmi";
    }
    else if ((headerInt3 == 0 || headerInt3 == 1) && (headerInt1 > 0 && headerInt1 <= 1024) && (headerInt2 > 0 && headerInt2 <= 1024)) {
      return "glb_tex";
    }
    else if (resource.getLength() == 768) {
      return "pal";
    }

    return null;
  }

}