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

import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Table;
import org.watto.component.WSTableColumn;
import org.watto.datatype.Archive;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_LOC;
import org.watto.io.FileManipulator;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_LOC_LANG extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_LOC_LANG() {
    super("LOC_LANG", "LOC_LANG Language Table");
    setExtensions("lang");

    setGames("The Great Escape");
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
  public boolean canEdit(PreviewPanel panel) {
    if (panel instanceof PreviewPanel_Table) {
      return true;
    }
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
      if (plugin instanceof Plugin_LOC) {
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

      // 4 - File Length (not including this field)
      if (FieldValidator.checkEquals(fm.readInt(), fm.getLength() - 4)) {
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

      // 4 - File Length (not including this field)
      // 4 - First Hash?
      fm.skip(8);

      // 4 - First String Offset [+4]
      int numKeys = fm.readInt() / 8;
      FieldValidator.checkNumFiles(numKeys);

      fm.seek(4);

      int[] hashes = new int[numKeys];
      int[] offsets = new int[numKeys];

      for (int k = 0; k < numKeys; k++) {
        // 4 - Hash
        int hash = fm.readInt();
        hashes[k] = hash;

        // 4 - String Offset [+4]
        int offset = fm.readInt() + 4;
        FieldValidator.checkOffset(offset);
        offsets[k] = offset;
      }

      Object[][] data = new Object[numKeys][3];
      for (int k = 0; k < numKeys; k++) {
        fm.relativeSeek(offsets[k]);

        // X - String (unicode)
        // 2 - null Unicode String Terminator
        String text = fm.readNullUnicodeString();

        int hash = hashes[k];

        data[k] = new Object[] { hash, text, text };
      }

      WSTableColumn[] columns = new WSTableColumn[3];
      columns[0] = new WSTableColumn("Hash", 'h', Integer.class, false, true, 0, 0); // hidden column 0,0
      columns[1] = new WSTableColumn("Original", 'o', String.class, false, true, 0, 0); // hidden column 0,0
      columns[2] = new WSTableColumn("Edited", 'e', String.class, true, true);

      PreviewPanel_Table preview = new PreviewPanel_Table(data, columns);

      return preview;

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
  public void edit(FileManipulator originalFM, PreviewPanel preview, FileManipulator fm) {
    // We can't write from scratch, but we can Edit the old file

    try {
      // should only be triggered from Table panels
      if (!(preview instanceof PreviewPanel_Table)) {
        return;
      }
      PreviewPanel_Table previewPanel = (PreviewPanel_Table) preview;

      // Get the table data from the preview (which are the edited ones), so we know which ones have been changed, and can put that data
      // into the "fm" as we read through and replicate the "originalFM"
      Object[][] data = previewPanel.getData();
      if (data == null) {
        return;
      }

      int numRows = data.length;

      // work out the length of the text data + directory
      int totalLength = numRows * 8;
      for (int k = 0; k < numRows; k++) {
        Object[] currentRow = data[k];
        totalLength += (((String) currentRow[2]).length()) * 2 + 2; // *2 to make it unicode, +2 for the unicode terminator
      }

      // 4 - File Length (not including this field)
      fm.writeInt(totalLength);

      int textOffset = (numRows * 8);

      // Directory
      for (int k = 0; k < numRows; k++) {
        Object[] currentRow = data[k];

        // 4 - Hash
        fm.writeInt((Integer) currentRow[0]);

        // 4 - String Offset
        fm.writeInt(textOffset);

        textOffset += (((String) currentRow[2]).length()) * 2 + 2; // *2 to make it unicode, +2 for the unicode terminator
      }

      // Texts
      for (int k = 0; k < numRows; k++) {
        Object[] currentRow = data[k];

        // X - String (unicode)
        String text = (String) currentRow[2];
        fm.writeUnicodeString(text);

        // 2 - null Unicode String Terminator
        fm.writeByte(0);
        fm.writeByte(0);
      }

      fm.close();
      originalFM.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public void write(PreviewPanel panel, FileManipulator destination) {

  }

  /**
  **********************************************************************************************
  Called when dragging in a CSV or tab-separated file (for example), and converting to this format  
  **********************************************************************************************
  **/
  public void replace(Resource resourceBeingReplaced, PreviewPanel preview, FileManipulator fm) {
    try {

      // should only be triggered from Table panels
      if (!(preview instanceof PreviewPanel_Table)) {
        return;
      }
      PreviewPanel_Table previewPanel = (PreviewPanel_Table) preview;

      // WE DON'T NEED THE SOURCE FILE IN THIS PARTICULAR PLUGIN, BUT IS HERE FOR REFERENCE FOR OTHER VIEWERS THAT MAY NEED IT.
      // ALSO NEED TO UNCOMMENT OUT THE CLOSE() AT THE END OF THIS METHOD
      /*
      // Extract the original resource into a byte[] array, so we can reference it
      int srcLength = (int) resourceBeingReplaced.getDecompressedLength();
      
      byte[] srcBytes = new byte[srcLength];
      FileManipulator src = new FileManipulator(new ByteBuffer(srcBytes));
      resourceBeingReplaced.extract(src);
      src.seek(0);
      */

      // Get the table data from the preview (which are the edited ones), so we know which ones have been changed, and can put that data
      // into the "fm" as we read through and replicate the "originalFM"
      Object[][] data = previewPanel.getData();
      if (data == null) {
        return;
      }

      int numRows = data.length;

      // determine if the table has a header row or not
      boolean hasHeader = false;
      Object[] headerRow = data[0];
      if (headerRow[0].toString().equals("Hash")) {
        // it has a header row
        hasHeader = true;
      }

      int startRow = 0;
      if (hasHeader) {
        startRow = 1;
      }

      // work out the length of the text data + directory
      int totalLength = (numRows - startRow) * 8;
      for (int k = startRow; k < numRows; k++) {
        Object[] currentRow = data[k];
        totalLength += (((String) currentRow[2]).length()) * 2 + 2; // *2 to make it unicode, +2 for the unicode terminator
      }

      // 4 - File Length (not including this field)
      fm.writeInt(totalLength);

      int textOffset = ((numRows - startRow) * 8);

      // Directory
      for (int k = startRow; k < numRows; k++) {
        Object[] currentRow = data[k];

        // 4 - Hash
        Object hashValue = currentRow[0];
        if (hashValue instanceof Integer) {
          fm.writeInt((Integer) hashValue);
        }
        else { // probably a String, but just in case, treat it like one anyway
          fm.writeInt(Integer.parseInt(hashValue.toString()));
        }

        // 4 - String Offset
        fm.writeInt(textOffset);

        textOffset += (((String) currentRow[2]).length()) * 2 + 2; // *2 to make it unicode, +2 for the unicode terminator
      }

      // Texts
      for (int k = startRow; k < numRows; k++) {
        Object[] currentRow = data[k];

        // X - String (unicode)
        String text = (String) currentRow[2];
        fm.writeUnicodeString(text);

        // 2 - null Unicode String Terminator
        fm.writeByte(0);
        fm.writeByte(0);
      }

      //src.close();
      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}