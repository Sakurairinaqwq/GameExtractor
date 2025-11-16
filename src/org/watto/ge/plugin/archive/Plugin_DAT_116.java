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
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;
import org.watto.xml.XMLNode;
import org.watto.xml.XMLReader;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_DAT_116 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_DAT_116() {

    super("DAT_116", "DAT_116");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Artist Colony");
    setExtensions("dat"); // MUST BE LOWER CASE
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

      File xmlFile = new File(fm.getFile().getParent() + File.separatorChar + "_structure.xml");
      if (xmlFile.exists() && xmlFile.isFile()) {
        rating += 50;
      }
      else {
        rating = 0;
      }

      return rating;

    }
    catch (Throwable t) {
      return 0;
    }
  }

  int realNumFiles = 0;

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

      long arcSize = path.length();

      File xmlFile = new File(path.getParent() + File.separatorChar + "_structure.xml");
      if (xmlFile.exists() && xmlFile.isFile()) {
        // OK
      }
      else {
        return null;
      }

      FileManipulator fm = new FileManipulator(xmlFile, false);

      fm.skip(3);
      String header = fm.readString(38);
      fm.skip(2);

      if (!header.equals("<?xml version=\"1.0\" encoding=\"utf-8\"?>")) {
        return null;
      }

      XMLNode root = XMLReader.read(fm);
      if (root == null) {
        return null;
      }

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      realNumFiles = 0;
      readDirectory(root, path, resources, arcSize, "");

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

  public void readDirectory(XMLNode root, File path, Resource[] resources, long arcSize, String dirName) {
    try {

      XMLNode[] children = root.getChildren();
      int numChildren = children.length;

      // Loop through directory
      for (int i = 0; i < numChildren; i++) {

        XMLNode child = children[i];

        String offsetString = child.getAttribute("rgbOffset");
        String lengthString = child.getAttribute("rgbSize");
        String alphaLengthString = child.getAttribute("alphaSize");

        if (offsetString != null && lengthString != null) {
          // a file

          // most files don't have a "name", if they don't just use tagname and child number
          String name = child.getAttribute("name");
          if (name == null || name.equals("")) {
            name = child.getTag() + (i + 1);
          }

          String filename = dirName + name + ".dat_tex";

          long offset = -1;
          long length = -1;
          try {
            offset = Long.parseLong(offsetString);
            length = Long.parseLong(lengthString);

            FieldValidator.checkOffset(offset, arcSize);
            FieldValidator.checkLength(length, arcSize);
          }
          catch (Throwable t) {
            // not a number
          }

          try {
            long alphaLength = Long.parseLong(alphaLengthString);
            FieldValidator.checkLength(alphaLength, arcSize);
            length += alphaLength;
          }
          catch (Throwable t) {
            // not a number
          }

          if (offset != -1 && length != -1) {
            //path,name,offset,length,decompLength,exporter
            resources[realNumFiles] = new Resource(path, filename, offset, length);
            realNumFiles++;

            TaskProgressManager.setValue(offset);
          }

        }
        else if (child.hasChildren()) {
          // maybe a directory

          String name = child.getAttribute("name");

          String subDirName = dirName;
          if (name != null && !name.equals("")) {
            subDirName += name + "\\";
          }

          readDirectory(child, path, resources, arcSize, subDirName);
        }
        else {
          // something else - don't care about it
        }

      }

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
