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
import org.watto.Settings;
import org.watto.component.PreviewPanel;
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.viewer.Viewer_LOC_LANG;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_LOC extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_LOC() {

    super("LOC", "LOC");

    //         read write replace rename
    setProperties(true, false, true, false);

    setGames("The Great Escape");
    setExtensions("loc"); // MUST BE LOWER CASE
    setPlatforms("PC");

    setFileTypes(new FileType("lang", "Language Document", FileType.TYPE_DOCUMENT));

    setCanConvertOnReplace(true);

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
      if (fm.readInt() == 0) {
        rating += 5;
      }

      // 4 - Number of Names
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      // 4 - Name Length (including null)
      if (FieldValidator.checkFilenameLength(fm.readInt())) {
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

      // 4 - Header (null)
      fm.skip(4);

      // 4 - Number of Names
      int numNames = fm.readInt();
      FieldValidator.checkNumFiles(numNames);

      for (int n = 0; n < numNames; n++) {
        // 4 - Name Length (including null)
        int nameLength = fm.readInt();
        FieldValidator.checkFilenameLength(nameLength);

        // X - Filename
        // 1 - null Filename Terminator
        fm.skip(nameLength);
      }

      // 4 - Number of Strings in each File
      fm.skip(4);

      // 4 - Number of Languages
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {
        // 4 - Language File Offset (relative to the END of this field)
        long offset = fm.readInt() + fm.getOffset();
        FieldValidator.checkOffset(offset, arcSize);

        String filename = "Language " + (i + 1) + ".lang";

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset);

        TaskProgressManager.setValue(i);
      }

      calculateFileSizes(resources, arcSize);

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

      FileManipulator fm = new FileManipulator(path, true);
      FileManipulator src = new FileManipulator(new File(Settings.getString("CurrentArchive")), false);

      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      // Calculations
      TaskProgressManager.setMessage(Language.get("Progress_PerformingCalculations"));

      // Write Header Data

      // 4 - Header (null)
      fm.writeBytes(src.readBytes(4));

      // 4 - Number of Names
      int numNames = src.readInt();
      fm.writeInt(numNames);

      for (int n = 0; n < numNames; n++) {
        // 4 - Name Length (including null)
        int nameLength = src.readInt();
        fm.writeInt(nameLength);

        // X - Filename
        // 1 - null Filename Terminator
        fm.writeBytes(src.readBytes(nameLength));
      }

      // 4 - Number of Strings in each File
      fm.writeBytes(src.readBytes(4));

      // 4 - Number of Languages
      fm.writeBytes(src.readBytes(4));

      // Write Directory
      TaskProgressManager.setMessage(Language.get("Progress_WritingDirectory"));
      long sizeSoFar = (numFiles - 1) * 4;

      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        long length = resource.getDecompressedLength();

        // 4 - Language File Offset (relative to the END of this field)
        fm.writeInt(sizeSoFar);

        sizeSoFar += length;
        sizeSoFar -= 4;
      }

      // Write Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      write(resources, fm);

      src.close();
      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

  /**
   **********************************************************************************************
   When replacing files, if the file is of a certain type, it will be converted before replace
   @param resourceBeingReplaced the Resource in the archive that is being replaced
   @param fileToReplaceWith the file on your PC that will replace the Resource. This file is the
          one that will be converted into a different format, if applicable.
   @return the converted file, if conversion was applicable/successful, else the original fileToReplaceWith
   **********************************************************************************************
   **/
  @Override
  public File convertOnReplace(Resource resourceBeingReplaced, File fileToReplaceWith) {
    try {

      PreviewPanel tablePreviewPanel = loadFileForConversion(resourceBeingReplaced, fileToReplaceWith, "lang");
      if (tablePreviewPanel == null) {
        // no conversion needed, or wasn't able to be converted
        return fileToReplaceWith;
      }

      // The plugin that will do the conversion
      Viewer_LOC_LANG converterPlugin = new Viewer_LOC_LANG();

      String beingReplacedExtension = resourceBeingReplaced.getExtension();
      File destination = new File(fileToReplaceWith.getAbsolutePath() + "." + beingReplacedExtension);
      if (destination.exists()) {
        destination.delete();
      }

      FileManipulator fmOut = new FileManipulator(destination, true);
      converterPlugin.replace(resourceBeingReplaced, tablePreviewPanel, fmOut);
      fmOut.close();

      return destination;

    }
    catch (Throwable t) {
      ErrorLogger.log(t);
      return fileToReplaceWith;
    }
  }

}
