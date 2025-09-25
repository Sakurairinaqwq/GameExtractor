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
import java.util.HashMap;

import org.watto.Settings;
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_REZ extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_REZ() {

    super("REZ", "REZ");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("The Operative: No One Lives Forever");
    setExtensions("rez"); // MUST BE LOWER CASE
    setPlatforms("PS2");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("dtx", "DTX Image", FileType.TYPE_IMAGE));

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

      if (fm.readInt() == 3) {
        rating += 5;
      }

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      if (fm.readInt() == 10) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // First File Offset
      if (FieldValidator.checkOffset(fm.readInt() * 2048, arcSize)) {
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

      // 4 - Unknown (3)
      fm.skip(4);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Hash Key (10)
      int hashKey = fm.readInt();

      // 4 - First File Offset [*2048]
      // 4 - Number of times that the directory is repeated (506)
      // 2028 - null Padding to offset 2048
      fm.seek(2048);

      // See if we have a file with the filenames in it, and if so, we need to read them in so the decryption works properly
      HashMap<Integer, String> hashMap = new HashMap<Integer, String>(numFiles);
      File hashFile = new File(Settings.get("HashesDirectory") + File.separatorChar + "REZ" + File.separatorChar + path.getName() + ".txt");
      if (hashFile.exists()) {
        int hashFileLength = (int) hashFile.length();

        FileManipulator hashFM = new FileManipulator(hashFile, false);
        while (hashFM.getOffset() < hashFileLength) {
          String name = hashFM.readLine();
          if (name.equals("")) {
            break; // EOF
          }
          int hash = calculateHash(name, hashKey);
          hashMap.put(hash, name);
          //System.out.println(hash + "\t" + name);
        }
        hashFM.close();
      }

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {

        // 4 - Hash
        int hash = fm.readInt();
        String filename = hashMap.get(hash);
        //System.out.println(hash + "\t" + filename);

        // 4 - File Offset [*2048]
        int offset = fm.readInt() * 2048;
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length (not including padding)
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - File Length (including padding) [*2048]
        fm.skip(4);

        if (filename == null) {
          filename = Resource.generateFilename(i);
        }

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
   Calculates the Hash for a Filename
   **********************************************************************************************
   **/
  int calculateHash(String originalFilename, int hashKey) {
    int uVar1;
    int iVar2 = 0;
    int iVar3 = 0;

    String filename = originalFilename.toUpperCase();

    byte[] characters = filename.getBytes();
    int numCharacters = characters.length;

    for (int c = 0; c < numCharacters; c++) { //while (*(char*)(filename + iVar2) != '\0') {

      uVar1 = ByteConverter.unsign(characters[iVar2]);//uVar1 = toupper((int)*(char*)(filename + iVar2));
      uVar1 = uVar1 & 0xff;
      if (uVar1 == 0x2f) {
        uVar1 = 0x5c;
      }
      iVar2 = iVar2 + 1;
      iVar3 = iVar3 + uVar1 + iVar3 * hashKey;
    }

    return iVar3;
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
