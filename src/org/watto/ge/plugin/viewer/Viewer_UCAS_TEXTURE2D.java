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

package org.watto.ge.plugin.viewer;

import org.watto.ErrorLogger;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_UCAS;
import org.watto.io.FileManipulator;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_UCAS_TEXTURE2D extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_UCAS_TEXTURE2D() {
    super("UCAS_TEXTURE2D", "UCAS_TEXTURE2D Image");
    setExtensions("texture2d");

    setGames("Sapphire Safari");
    setPlatforms("PC");
    setStandardFileFormat(false);
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public boolean canWrite(PreviewPanel panel) {
    return false;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public boolean canReplace(PreviewPanel panel) {
    return false;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public int getMatchRating(FileManipulator fm) {
    try {

      int rating = 0;

      ArchivePlugin plugin = Archive.getReadPlugin();
      if (plugin instanceof Plugin_UCAS) {
        rating += 50;
      }
      else if (!(plugin instanceof AllFilesPlugin)) {
        return 0;
      }

      if (FieldValidator.checkExtension(fm, extensions)) {
        rating += 25;
      }
      else {
        return 0;
      }

      fm.skip(48);

      long arcSize = fm.getLength();

      // 4 - File Data Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // 4 - Number of Names
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      // 4 - Name Dir Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
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
  Reads a resource from the FileManipulator, and generates a PreviewPanel for it. The FileManipulator
  is an extracted temp file, not the original archive!
  **********************************************************************************************
  **/
  @Override
  public PreviewPanel read(FileManipulator fm) {
    try {

      ImageResource imageResource = readThumbnail(fm);

      if (imageResource == null) {
        return null;
      }

      PreviewPanel_Image preview = new PreviewPanel_Image(imageResource);

      return preview;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

  /**
  **********************************************************************************************
  Reads a resource from the FileManipulator, and generates a Thumbnail for it (generally, only
  an Image ViewerPlugin will do this, but others can do it if they want). The FileManipulator is
  an extracted temp file, not the original archive!
  **********************************************************************************************
  **/

  @Override
  public ImageResource readThumbnail(FileManipulator fm) {
    try {

      long arcSize = fm.getLength();

      // 48 - Unknown
      fm.skip(48);

      // 4 - File Data Offset
      int fileDataOffset = fm.readInt();
      FieldValidator.checkOffset(fileDataOffset, arcSize);

      /*
      // 4 - Number of Names
      int nameCount = fm.readInt();
      FieldValidator.checkNumFiles(nameCount);
      
      // 4 - Name Directory Length
      int nameDirLength = fm.readInt();
      FieldValidator.checkLength(nameDirLength, arcSize);
      
      // 4 - Unknown
      // 4 - Unknown
      fm.skip(8);
      
      // numNames*8 - Unknown
      fm.skip(nameCount * 8);
      
      int[] nameLengths = new int[nameCount];
      // for each name
      for (int i = 0; i < nameCount; i++) {
        //   2 - Name Length
        int nameLength = ShortConverter.changeFormat(fm.readShort());
        FieldValidator.checkFilenameLength(nameLength);
        nameLengths[i] = nameLength;
      }
      
      String[] names = new String[nameCount];
      // for each name
      String type = null;
      for (int i = 0; i < nameCount; i++) {
        //   X - Name
        String name = fm.readString(nameLengths[i]);
        names[i] = name;
      
        if (name.startsWith("PF_")) {
          type = name;
          break;
        }
      
      }
      */

      fm.seek(fileDataOffset);

      // 8 - null
      // 4 - Unknown
      fm.skip(12);

      // 4 - Unknown (optional)
      // 4 - Image Width
      int widthCheck = fm.readInt();
      try {
        FieldValidator.checkWidth(widthCheck);
      }
      catch (Throwable t) {
        widthCheck = fm.readInt();
        FieldValidator.checkWidth(widthCheck);
      }

      // 4 - Image Height
      // 19 - Unknown
      // 4 - null
      for (int i = 0; i < 50; i++) {
        if (fm.readByte() == 0) {
          if (fm.readByte() == 0) {
            if (fm.readByte() == 0) {
              if (fm.readByte() == 0) {
                // found the 4 nulls
                break;
              }
            }
          }
        }
      }

      while (fm.readByte() == 0 && fm.getOffset() < arcSize) {
        // keep reading until a non-null is found
      }

      // 2 - Unknown
      // 2 - Unknown
      // 4 - Unknown
      // 4 - Unknown
      // 4 - Unknown
      // 4 - null
      // 4 - Image Data Length
      // 20 - null
      fm.skip(43); // 44-1 because 1 byte read in the while loop above

      // 4 - Image Width
      int width = fm.readInt();
      FieldValidator.checkWidth(width);

      // 4 - Image Height
      int height = fm.readInt();
      FieldValidator.checkHeight(height);

      // 4 - Unknown
      fm.skip(4);

      // 4 - Image Format Length (including null terminator)
      int imageFormatLength = fm.readInt();
      FieldValidator.checkFilenameLength(imageFormatLength);

      // X - Image Format String
      // 1 - null Image Format String Terminator
      String imageFormat = fm.readNullString(imageFormatLength);

      // 4 - null
      // 4 - Unknown
      // 4 - null
      fm.skip(12);

      // X - Image Data
      ImageResource imageResource = null;
      if (imageFormat.equals("PF_DXT1")) {
        imageResource = ImageFormatReader.readDXT1(fm, width, height);
      }
      else if (imageFormat.equals("PF_DXT3")) {
        imageResource = ImageFormatReader.readDXT3(fm, width, height);
      }
      else if (imageFormat.equals("PF_DXT5")) {
        imageResource = ImageFormatReader.readDXT5(fm, width, height);
      }
      else if (imageFormat.equals("PF_B8G8R8A8")) {
        imageResource = ImageFormatReader.readBGRA(fm, width, height);
      }
      else if (imageFormat.equals("PF_BC4")) {
        imageResource = ImageFormatReader.readBC4(fm, width, height);
      }
      else if (imageFormat.equals("PF_BC5")) {
        imageResource = ImageFormatReader.readBC5(fm, width, height);
      }
      else if (imageFormat.equals("PF_BC7")) {
        imageResource = ImageFormatReader.readBC7(fm, width, height);
      }
      else if (imageFormat.equals("PF_G8")) {
        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height);
      }
      else {
        ErrorLogger.log("[Viewer_UCAS_TEXTURE2D] Unknown image format: " + imageFormat);
      }

      fm.close();

      return imageResource;

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
  public void write(PreviewPanel preview, FileManipulator fm) {
  }

}