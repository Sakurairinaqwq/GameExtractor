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
import java.io.FilenameFilter;
import java.util.Arrays;

import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileExtensionFilter;
import org.watto.io.FileManipulator;
import org.watto.io.FilenameSplitter;
import org.watto.io.converter.ByteConverter;
import org.watto.io.converter.IntConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_PKB_2 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_PKB_2() {

    super("PKB_2", "PKB_2");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Shadow Hearts 2",
        "Shadow Hearts Covenant");
    setExtensions("pkb"); // MUST BE LOWER CASE
    setPlatforms("PS2");

    setFileTypes(new FileType("pkb_arc", "Archive", FileType.TYPE_ARCHIVE),
        new FileType("bmt", "Bitmap Image", FileType.TYPE_IMAGE));

    //setTextPreviewExtensions("colours", "rat", "screen", "styles"); // LOWER CASE

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

      // look for the PKFINFO.TBL directory file
      File dirPath = new File(fm.getFile().getParentFile().getAbsolutePath() + File.separatorChar + "PKFINFO.TBL");
      if (dirPath != null && dirPath.exists() && dirPath.isFile()) {
        rating += 24; // slightly lower than "PKB" so that it picks that format first, before picking this one.
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
      realNumFiles = 0;

      // look for the PKFINFO.TBL directory file
      File sourcePath = new File(path.getParentFile().getAbsolutePath() + File.separatorChar + "PKFINFO.TBL");
      if (sourcePath != null && sourcePath.exists() && sourcePath.isFile()) {
        // all OK
      }
      else {
        return null;
      }

      // Now find all the PKB archives
      File[] archiveFiles = sourcePath.getParentFile().listFiles((FilenameFilter) new FileExtensionFilter("PKB"));
      Arrays.sort(archiveFiles); // sort alphabetically
      int numArchives = archiveFiles.length;

      FileManipulator fm = new FileManipulator(sourcePath, false);
      long tblLength = fm.getLength();

      // 4 - Unknown
      // 4 - TBL File Length [+16]
      // 8 - null
      // 4 - Unknown
      // 4 - Unknown
      // 4 - null
      // 4 - Unknown
      // 4 - Unknown
      // 4 - Unknown
      // 4 - null
      // 4 - Unknown
      // 4 - Unknown
      // 4 - Unknown
      // 4 - null
      // 4 - Unknown
      fm.skip(64);

      int[] dirOffsets = new int[numArchives];
      for (int a = 0; a < numArchives; a++) {
        // 4 - Unknown
        // 4 - Unknown
        // 4 - null
        fm.skip(12);

        // 3 - Directory Offset for this Archive [+16]
        // 1 - Dir Entry Flag (128)
        byte[] offsetBytes = fm.readBytes(4);
        offsetBytes[3] &= 127;
        int dirOffset = IntConverter.convertLittle(offsetBytes) + 16;
        FieldValidator.checkOffset(dirOffset, tblLength);
        dirOffsets[a] = dirOffset;
      }

      int numFiles = (int) tblLength / 16; // max
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      realNumFiles = 0;

      // the PKB archives aren't in alphabetical order, so we need to read through the TBL file, determine the archive lengths, and then put the files in the right order.
      long[] arcSizesInOrder = new long[numArchives];
      for (int a = 0; a < numArchives; a++) {
        int dirOffset = dirOffsets[a];
        int dirLength = (int) tblLength;
        if (a + 1 < numArchives) {
          dirLength = dirOffsets[a + 1];
        }
        dirLength -= dirOffset;
        int endDirOffset = dirOffset + dirLength;

        int numFilesInDir = dirLength / 16;
        FieldValidator.checkNumFiles(numFilesInDir);

        fm.seek(dirOffset);
        arcSizesInOrder[a] = readDirectoryToDetermineArchiveLength(fm, dirOffset, endDirOffset, 0);
      }

      // now we have all the arc sizes, so we need to arrange the array properly

      File[] archiveFilesAlphabetical = archiveFiles;
      archiveFiles = new File[numArchives];
      for (int a = 0; a < numArchives; a++) {
        File archiveFile = archiveFilesAlphabetical[a];
        long archiveLength = archiveFile.length();
        for (int b = 0; b < numArchives; b++) {
          if (arcSizesInOrder[b] == archiveLength) {
            // found a match
            //System.out.println(b + " is " + archiveFile.getName());
            archiveFiles[b] = archiveFile;
            break;
          }
        }
      }

      // Now the archives are in the right order, so we can go forward and read/store the files properly.
      for (int a = 0; a < numArchives; a++) {
        int dirOffset = dirOffsets[a];
        int dirLength = (int) tblLength;
        if (a + 1 < numArchives) {
          dirLength = dirOffsets[a + 1];
        }
        dirLength -= dirOffset;
        int endDirOffset = dirOffset + dirLength;

        int numFilesInDir = dirLength / 16;
        FieldValidator.checkNumFiles(numFilesInDir);

        File arcFile = archiveFiles[a];
        long arcSize = arcFile.length();

        //System.out.println(arcFile.getName() + " at " + dirOffset);

        String parentName = FilenameSplitter.getFilename(arcFile);

        fm.seek(dirOffset);
        readDirectory(fm, resources, arcFile, arcSize, parentName + "\\", dirOffset, endDirOffset, 0);
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
  public long readDirectoryToDetermineArchiveLength(FileManipulator fm, long dirOffset, long endDirOffset, long relativeOffset) {
    try {

      long endOffset = 0;

      int maxSubDirs = (int) (endDirOffset - dirOffset) / 16;

      long[] subDirOffsets = new long[maxSubDirs];
      long[] subDirEndOffsets = new long[maxSubDirs];
      long[] subDirRelativeOffsets = new long[maxSubDirs];

      int realNumSubDirs = 0;
      boolean foundFirstSub = false;
      for (int i = 0; i < maxSubDirs; i++) {
        // 4 - Unknown
        // 4 - Unknown
        fm.skip(8);

        // 4 - Relative Offset for these files in the PKB Archive [*16]
        long subDirRelativeOffset = ((long) fm.readInt()) * 16;
        FieldValidator.checkOffset(subDirRelativeOffset);

        // 3 - Directory Offset for this Archive (relative to the start of this PKB Archive Entry
        // 1 - Dir Entry Flag (128)
        byte[] offsetBytes = fm.readBytes(4);

        if ((ByteConverter.unsign(offsetBytes[3]) >> 7) == 1) {
          // a sub-dir

          if (!foundFirstSub) {
            byte[] subOffsetBytes = new byte[] { offsetBytes[0], offsetBytes[1], offsetBytes[2], (byte) (offsetBytes[3] & 127) };
            int subDirOffset = IntConverter.convertLittle(subOffsetBytes);
            maxSubDirs = subDirOffset / 16;
            foundFirstSub = true;
          }

          subDirRelativeOffset += relativeOffset;
          FieldValidator.checkOffset(subDirRelativeOffset);

        }
        else {
          // a file

          // get the offset and length from the fields read above
          long offset = subDirRelativeOffset + relativeOffset;

          offsetBytes[3] &= 127;

          long length = IntConverter.convertLittle(offsetBytes);
          FieldValidator.checkLength(length);

          long fileEndOffset = offset + length;
          if (fileEndOffset > endOffset) {
            endOffset = fileEndOffset;
          }

          continue;
        }

        offsetBytes[3] &= 127;
        int subDirOffset = IntConverter.convertLittle(offsetBytes) + (int) dirOffset;
        FieldValidator.checkOffset(dirOffset);
        subDirOffsets[realNumSubDirs] = subDirOffset;

        subDirRelativeOffsets[realNumSubDirs] = subDirRelativeOffset;

        if (realNumSubDirs != 0) {
          subDirEndOffsets[realNumSubDirs - 1] = subDirOffset;
        }

        realNumSubDirs++;
      }

      int numSubDirs = realNumSubDirs;

      if (numSubDirs != 0) {
        subDirEndOffsets[numSubDirs - 1] = endDirOffset;
      }

      // process each subdirectory
      for (int i = 0; i < numSubDirs; i++) {
        fm.seek(subDirOffsets[i]);
        long subEndOffset = readDirectoryToDetermineArchiveLength(fm, subDirOffsets[i], subDirEndOffsets[i], subDirRelativeOffsets[i]);

        if (subEndOffset > endOffset) {
          endOffset = subEndOffset;
        }

      }

      return endOffset;

    }
    catch (Throwable t) {
      logError(t);
      return 0;
    }
  }

  /**
   **********************************************************************************************
  
   **********************************************************************************************
   **/
  public void readDirectory(FileManipulator fm, Resource[] resources, File arcFile, long arcSize, String dirName, long dirOffset, long endDirOffset, long relativeOffset) {
    try {

      /*
      
      // find out how many sub-directories
      int numSubDirs = 0;
      
      // 4 - Unknown
      // 4 - Unknown
      // 4 - null
      fm.skip(12);
      
      // 3 - Directory Offset for this Archive [+16]
      // 1 - Dir Entry Flag (128)
      byte[] offsetBytes = fm.readBytes(4);
      if ((ByteConverter.unsign(offsetBytes[3]) >> 7) == 1) {
        // has sub-directories
      
        offsetBytes[3] &= 127;
        int subDirOffset = IntConverter.convertLittle(offsetBytes);
        numSubDirs = subDirOffset / 16;
        FieldValidator.checkNumFiles(numSubDirs);
      }
      
      fm.seek(dirOffset);
      */

      int maxSubDirs = (int) (endDirOffset - dirOffset) / 16;

      long[] subDirOffsets = new long[maxSubDirs];
      long[] subDirEndOffsets = new long[maxSubDirs];
      long[] subDirRelativeOffsets = new long[maxSubDirs];

      int realNumSubDirs = 0;
      boolean foundFirstSub = false;
      for (int i = 0; i < maxSubDirs; i++) {
        // 4 - Unknown
        // 4 - Unknown
        fm.skip(8);

        // 4 - Relative Offset for these files in the PKB Archive [*16]
        long subDirRelativeOffset = ((long) fm.readInt()) * 16;
        FieldValidator.checkOffset(subDirRelativeOffset, arcSize);

        // 3 - Directory Offset for this Archive (relative to the start of this PKB Archive Entry
        // 1 - Dir Entry Flag (128)
        byte[] offsetBytes = fm.readBytes(4);

        if ((ByteConverter.unsign(offsetBytes[3]) >> 7) == 1) {
          // a sub-dir

          if (!foundFirstSub) {
            byte[] subOffsetBytes = new byte[] { offsetBytes[0], offsetBytes[1], offsetBytes[2], (byte) (offsetBytes[3] & 127) };
            int subDirOffset = IntConverter.convertLittle(subOffsetBytes);
            maxSubDirs = subDirOffset / 16;
            foundFirstSub = true;
          }

          subDirRelativeOffset += relativeOffset;
          FieldValidator.checkOffset(subDirRelativeOffset, arcSize);

        }
        else {
          // a file

          // get the offset and length from the fields read above
          long offset = subDirRelativeOffset + relativeOffset;

          offsetBytes[3] &= 127;

          long length = IntConverter.convertLittle(offsetBytes);
          FieldValidator.checkLength(length, arcSize);

          String filename = dirName + Resource.generateFilename(realNumFiles);

          //path,name,offset,length,decompLength,exporter
          Resource resource = new Resource(arcFile, filename, offset, length);
          resource.forceNotAdded(true);
          resources[realNumFiles] = resource;
          realNumFiles++;

          TaskProgressManager.setValue(realNumFiles);

          continue;
        }

        offsetBytes[3] &= 127;
        int subDirOffset = IntConverter.convertLittle(offsetBytes) + (int) dirOffset;
        FieldValidator.checkOffset(dirOffset);
        subDirOffsets[realNumSubDirs] = subDirOffset;

        subDirRelativeOffsets[realNumSubDirs] = subDirRelativeOffset;

        if (realNumSubDirs != 0) {
          subDirEndOffsets[realNumSubDirs - 1] = subDirOffset;
        }

        realNumSubDirs++;
      }

      int numSubDirs = realNumSubDirs;

      if (numSubDirs != 0) {
        subDirEndOffsets[numSubDirs - 1] = endDirOffset;
      }

      /*
      if (numSubDirs == 0) {
        // read the files in this directory
        int numFiles = (int) (endDirOffset - dirOffset) / 16;
        for (int i = 0; i < numFiles; i++) {
          // 8 - Hash?
          fm.skip(8);
      
          // 4 - File Offset [*16 + relativeOffset]
          long offset = (fm.readInt() * 16) + relativeOffset;
          FieldValidator.checkOffset(offset, arcSize);
      
          // 4 - File Length
          long length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);
      
          String filename = dirName + Resource.generateFilename(realNumFiles);
      
          //path,name,offset,length,decompLength,exporter
          Resource resource = new Resource(arcFile, filename, offset, length);
          resource.forceNotAdded(true);
          resources[realNumFiles] = resource;
          realNumFiles++;
      
          TaskProgressManager.setValue(realNumFiles);
        }
      
      }
      else {
      */
      // process each subdirectory
      for (int i = 0; i < numSubDirs; i++) {
        fm.seek(subDirOffsets[i]);
        readDirectory(fm, resources, arcFile, arcSize, dirName + "Dir" + (i + 1) + "\\", subDirOffsets[i], subDirEndOffsets[i], subDirRelativeOffsets[i]);
      }
      /*
       }
      */

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

    // NEW ONES FROM GAME 2
    if (headerInt1 == 1213350995) {
      return "ase"; // confirmed ase, dse
    }
    else if (headerInt1 == 1196246597) {
      return "efd"; // confirmed
    }
    else if (headerInt1 == 152406) {
      return "vs";
    }
    else if (headerInt1 == 541344339) {
      return "mbs"; // confirmed
    }
    else if (headerInt1 == 66048) {
      return "66048";
    }
    else if (headerInt1 == 1212437324) {
      return "lsd";
    }
    else if (headerInt1 == 826823507) {
      return "ssh1"; // confirmed mdl, sdw, rw, rws, lw, lws
    }
    else if (headerInt1 == 1145590099) {
      return "anm"; // confirmed
    }
    else if (headerInt1 == 1161972054) {
      return "vbe"; // confirmed
    }

    // OLDER ONES FROM GAME 1 (still very relevant!)

    int fileLength = (int) resource.getLength();

    if (headerInt2 == fileLength) {
      return "pkb_arc"; // can be "mdl" (model) or "bin"
    }

    else if (headerInt1 == 1935959411) {
      return "sed"; // confirmed
    }
    else if (headerInt1 == 1835300723) {
      return "swd"; // confirmed
    }
    else if (headerInt1 == 1145652813) {
      return "mfid";
    }

    else if (headerInt1 == 1415071060) {
      return "text";
    }
    else if (headerInt1 == 1448496973) {
      return "movv";
    }
    else if (headerInt1 == 1094997325) {
      return "muda";
    }
    else if (headerInt1 == 1094795585) {
      return "aaaa";
    }
    else if (headerInt1 == -1174339584) {
      return "movie";
    }
    else if ((headerShort1 * headerShort2) + 1040 == fileLength || (headerShort1 * headerShort2) + 400 == fileLength || (headerShort1 * headerShort2) + 272 == fileLength) { // 1040 for 256 colors, 400 for 96 colors, 272 for 64 colors
      return "bmt"; // confirmed (also sometimes "bin")
    }
    else if (headerInt1 == headerInt2 && (headerShort1 > 0 && headerShort1 <= 8192) && (headerShort2 > 0 && headerShort2 <= 8192)) { // also an image, as the width/height are repeated
      return "bmt"; // confirmed (also sometimes "bin")
    }

    /*
    if (headerInt1 == 2037149520) {
      return "js";
    }
    */

    return null;
  }

}
