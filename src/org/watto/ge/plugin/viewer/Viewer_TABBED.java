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
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.io.FileManipulator;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_TABBED extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_TABBED() {
    super("TABBED", "Tab-Delimited Text File");
    setExtensions("xls");

    setGames("Generic Tabbed Table");
    setPlatforms("PC");
    setStandardFileFormat(false);
  }

  boolean hasHeaderLine = false; // by default, assume no header line

  public boolean getHasHeaderLine() {
    return hasHeaderLine;
  }

  public void setHasHeaderLine(boolean hasHeaderLine) {
    this.hasHeaderLine = hasHeaderLine;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public boolean canWrite(PreviewPanel panel) {
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

      /*
      ArchivePlugin plugin = Archive.getReadPlugin();
      if (!(plugin instanceof AllFilesPlugin)) {
        return 0;
      }
      */

      if (FieldValidator.checkExtension(fm, extensions)) {
        rating += 25;
      }
      else {
        return 0;
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

      long arcSize = fm.getLength();

      // read all the lines
      int numLines = Archive.getMaxFiles();
      int realNumLines = 0;
      String[] lines = new String[numLines];

      while (fm.getOffset() < arcSize) {
        String line = fm.readLine();
        lines[realNumLines] = line;
        realNumLines++;
      }

      if (realNumLines <= 0) {
        return null;
      }

      numLines = realNumLines;

      // take the first line, work out the number of columns
      String[] columnHeadings = lines[0].split("\t");
      int numColumns = columnHeadings.length;

      Object[][] data = null;
      WSTableColumn[] columns = null;

      if (hasHeaderLine) {
        //
        // the first line also should contain the column names
        //

        // set up the data array
        data = new Object[numLines - 1][numColumns]; // -1 to exclude the first row (headings)
        for (int k = 0, c = 1; c < numLines; k++, c++) {
          String line = lines[k];

          String[] currentColumnSplit = line.split("\t", numColumns); // numColumns so that, if this line has more columns, the last column will contain the remaining text
          int currentNumColumns = currentColumnSplit.length;

          for (int i = 0; i < currentNumColumns; i++) {
            data[k][i] = currentColumnSplit[i];
          }

          // empty values for the remaining fields, if there weren't enough columns in this line
          for (int i = currentNumColumns; i < numColumns; i++) {
            data[k][i] = "";
          }

        }

        fm.close();

        columns = new WSTableColumn[numColumns];
        for (int i = 0; i < numColumns; i++) {
          columns[i] = new WSTableColumn(columnHeadings[i], (char) (i + 32), String.class, true, true);
        }
      }
      else {
        // 
        // No header line
        //

        // set up the data array
        data = new Object[numLines][numColumns];
        for (int k = 0; k < numLines; k++) {
          String line = lines[k];

          String[] currentColumnSplit = line.split("\t", numColumns); // numColumns so that, if this line has more columns, the last column will contain the remaining text
          int currentNumColumns = currentColumnSplit.length;

          for (int i = 0; i < currentNumColumns; i++) {
            data[k][i] = currentColumnSplit[i];
          }

          // empty values for the remaining fields, if there weren't enough columns in this line
          for (int i = currentNumColumns; i < numColumns; i++) {
            data[k][i] = "";
          }

        }

        fm.close();

        columns = new WSTableColumn[numColumns];
        for (int i = 0; i < numColumns; i++) {
          columns[i] = new WSTableColumn("Column " + (i + 1), (char) (i + 32), String.class, true, true);
        }
      }

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
  public void write(PreviewPanel panel, FileManipulator fm) {
    try {
      // should only be triggered from Table panels
      if (!(panel instanceof PreviewPanel_Table)) {
        return;
      }
      PreviewPanel_Table previewPanel = (PreviewPanel_Table) panel;

      // Get the column headings, write them out first
      WSTableColumn[] headerColumns = previewPanel.getTableColumns();
      int numHeaderColumns = headerColumns.length;

      fm.writeString(headerColumns[0].getName()); // write the first value

      for (int i = 1; i < numHeaderColumns; i++) { // all subsequent values have a tab to separate them
        fm.writeString("\t" + headerColumns[i].getName());
      }

      // write a new line at the end
      fm.writeByte(13);
      fm.writeByte(10);

      // Get the table data from the preview (which are the edited ones), so we know which ones have been changed, and can put that data
      // into the "fm" as we read through and replicate the "originalFM"
      Object[][] data = previewPanel.getData();
      if (data == null) {
        return;
      }

      int numRows = data.length;

      for (int k = 0; k < numRows; k++) {
        Object[] currentRow = data[k];
        int numColumns = currentRow.length;

        fm.writeString(currentRow[0].toString()); // write the first value

        for (int i = 1; i < numColumns; i++) { // all subsequent values have a tab to separate them
          fm.writeString("\t" + currentRow[i].toString());
        }

        // write a new line at the end
        fm.writeByte(13);
        fm.writeByte(10);

      }

      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}