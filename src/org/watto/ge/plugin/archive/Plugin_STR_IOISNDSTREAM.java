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

import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.resource.Resource_WAV_RawAudio;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_STR_IOISNDSTREAM extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_STR_IOISNDSTREAM() {

    super("STR_IOISNDSTREAM", "STR_IOISNDSTREAM");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Hitman Blood Money");
    setExtensions("str");
    setPlatforms("PC", "PS2");

    //setFileTypes("","",
    //             "",""
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

      // Header
      if (fm.readString(12).equals("IOISNDSTREAM")) {
        rating += 50;
      }

      if (fm.readInt() == 9) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Directory Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      // 4 - Padding Multiple (256/2048)
      int padding = fm.readInt();
      if (padding == 256 || padding == 2048) {
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

      // 12 - Header (IOISNDSTREAM)
      // 4 - Unknown (9)
      fm.skip(16);

      // 4 - Directory Offset
      long dirOffset = fm.readInt();
      FieldValidator.checkOffset(dirOffset, arcSize);

      // 4 - Number Of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Padding Multiple (256/2048)
      // 4 - null
      // 4 - Unknown (2)
      // 4 - Unknown (1)
      // X - null padding to the Padding Multiple
      fm.seek(dirOffset);

      Resource[] resources = new Resource[numFiles * 2];// *2 as we want to store the audio data and the lipsync data separately
      TaskProgressManager.setMaximum(numFiles);
      int realNumFiles = 0;

      // Loop through directory
      long[] nameOffsets = new long[numFiles];
      int[] nameLengths = new int[numFiles];
      long[] propertiesOffsets = new long[numFiles];
      for (int i = 0; i < numFiles; i++) {
        // 8 - File ID?
        fm.skip(8);

        // 8 - File Offset
        long offset = fm.readLong();
        FieldValidator.checkOffset(offset, arcSize);

        // 8 - File Length (not including Padding, not including lipsync data)
        long length = fm.readLong();
        FieldValidator.checkLength(length, arcSize);

        // 8 - Audio Properties Offset
        long propertiesOffset = fm.readLong();
        FieldValidator.checkOffset(propertiesOffset, arcSize);
        propertiesOffsets[i] = propertiesOffset;

        // 4 - Audio Properties Entry Size (20/24)
        // 4 - Unknown
        fm.skip(8);

        // 8 - Filename Length (not including null)
        int filenameLength = (int) fm.readLong();
        FieldValidator.checkFilenameLength(filenameLength);
        nameLengths[i] = filenameLength;

        // 8 - Filename Offset
        long filennameOffset = fm.readInt();
        fm.skip(4);
        FieldValidator.checkOffset(filennameOffset, arcSize);
        nameOffsets[i] = filennameOffset;

        // 4 - LipSync Data (0=No lipsync data, 4=Has lipsync data)
        //int hasLipsync = fm.readInt();
        fm.skip(4);

        // 8 - Unknown (0/16/32/48/64)
        // 4 - null
        fm.skip(12);

        /*
        if (hasLipsync == 0) {
          // no lipsync
        
          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource_WAV_RawAudio(path, null, offset, length);
          realNumFiles++;
        }
        else if (hasLipsync == 4) {
        */
        // 4096 bytes of lipsync data before the audio

        // Store the first file as the lipsync data
        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, ".lip", offset, 4096);
        realNumFiles++;

        // Store a second file as the audio data
        resources[realNumFiles] = new Resource_WAV_RawAudio(path, null, offset, length);
        realNumFiles++;

        /*
        }
        else {
        ErrorLogger.log("[STR_IOISNDSTREAM] Unknown lipsync flag: " + hasLipsync);
        }
        */

        TaskProgressManager.setValue(i);
      }

      resources = resizeResources(resources, realNumFiles);

      fm.getBuffer().setBufferSize(128); // small quick reads

      // now go and grab the filenames and the audio properties
      // also check if there's lipsync data or not
      realNumFiles = 0;
      for (int i = 0; i < numFiles; i++) {
        Resource lipResource = resources[realNumFiles];
        Resource audioResource = resources[realNumFiles + 1];

        long offset = lipResource.getOffset();

        // LIPSYNC
        fm.seek(offset);
        if (fm.readString(4).equals("LIP ")) {
          // has lipsync data

          // need to move the audio data along by 4096 bytes
          audioResource.setOffset(offset + 4096);
        }
        else {
          // no lipsync data

          // need to set the lipsync length to 0
          lipResource.setLength(0);
          lipResource.setDecompressedLength(0);
        }

        // NAME
        fm.seek(nameOffsets[i]);

        // X - Filename
        // 1 - null Filename Terminator
        String name = fm.readString(nameLengths[i]);

        audioResource.setName(name);
        audioResource.setOriginalName(name);

        name += ".lip";
        lipResource.setName(name);
        lipResource.setOriginalName(name);

        // AUDIO PROPERTIES
        fm.seek(propertiesOffsets[i]);

        // 4 - Entry Type (9/6) (also audio format - not sure what codecs they represent yet)
        // 4 - File Data Length
        fm.skip(8);

        // 4 - Number of Channels (1/2)
        short channels = (short) fm.readInt();

        // 4 - Frequency (16000/32000)
        int frequency = fm.readInt();

        // 4 - Audio Bitrate (8/16)
        short bitrate = (short) fm.readInt();

        ((Resource_WAV_RawAudio) audioResource).setAudioProperties(frequency, bitrate, channels);

        realNumFiles += 2;

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

}
