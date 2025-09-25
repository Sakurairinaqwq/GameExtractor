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
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.datatype.PalettedImageResource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.helper.PaletteManager;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_DAS_DASP;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;
import org.watto.io.converter.ShortConverter;

/**
**********************************************************************************************
Ref: https://github.com/radishengine/denormalize/blob/master/DAS.js
**********************************************************************************************
**/
public class Viewer_DAS_DASP_DASTEX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_DAS_DASP_DASTEX() {
    super("DAS_DASP_DASTEX", "DAS_DASP_DASTEX Image");
    setExtensions("das_tex");

    setGames("Normality");
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
      if (plugin instanceof Plugin_DAS_DASP) {
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

      fm.skip(2);

      // 2 - Image Width
      if (FieldValidator.checkWidth(fm.readShort())) {
        rating += 5;
      }

      // 2 - Image Height
      if (FieldValidator.checkHeight(fm.readShort())) {
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

      // 2 - Flags (animated = Flags & 0x100) (stencil = Flags & 0x200)
      int flags = fm.readShort();

      // 2 - Image Width
      int width = fm.readShort();
      FieldValidator.checkWidth(width);

      // 2 - Image Height
      int height = fm.readShort();
      FieldValidator.checkHeight(height);

      ImageResource imageResource = null;
      int imageSize = (width * height) + 6;
      if (arcSize == imageSize || arcSize == imageSize + 1) {
        // Single Image
        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, true);
        imageResource = ImageFormatReader.rotateLeft(imageResource);
        imageResource = ImageFormatReader.flipVertically(imageResource);
      }
      else {
        // Multiple images

        // 4 - File Length
        int fileLength = fm.readInt();
        if (fileLength == arcSize || (flags & 0x100) == 0x100) {
          // an animation

          // 2 - First Image Offset
          int firstImageOffset = fm.readShort();

          // 2 - Number of Frames
          // 2 - Unknown (-1)
          fm.skip(4);

          // 1 - Animation Speed
          int speed = fm.readByte();
          if (speed == 0x02) {
            speed = 70;
          }
          else if (speed == 0x03) {
            speed = 60;
          }
          else if (speed == 0x04) {
            speed = 100;
          }
          else if (speed == 0x06) {
            speed = 130;
          }
          else if (speed == 0x07) {
            speed = 170;
          }
          else if (speed == 0x08) {
            speed = 190;
          }
          else if (speed == 0x0A) {
            speed = 210;
          }
          else if (speed == 0x0E) {
            speed = 270;
          }
          else if (speed == 0x10) {
            speed = 60;
          }
          else {
            speed = 100;
          }

          // 1 - Unknown
          fm.skip(1);

          int numImages = firstImageOffset / 4; // max
          int[] imageOffsets = new int[numImages];
          imageOffsets[0] = firstImageOffset;

          int realNumImages = 1; // because we've already used the first one above
          while (fm.getOffset() < firstImageOffset - 2) {
            // 4 - Image Offset (can be null)
            int imageOffset = fm.readInt();
            if (imageOffset != 0) {
              imageOffsets[realNumImages] = imageOffset - 10; // not sure why -10
              realNumImages++;
            }
          }

          numImages = realNumImages;
          ImageResource[] images = new ImageResource[numImages];
          for (int i = 0; i < numImages; i++) {
            fm.relativeSeek(imageOffsets[i]);

            if (i == 0) {
              // the first image, which is a full image

              // 2 - Flags
              fm.skip(2);

              // 2 - Image Width
              width = fm.readShort();
              FieldValidator.checkWidth(width);

              // 2 - Image Height
              height = fm.readShort();
              FieldValidator.checkHeight(height);

              // X - Image Data (palette indexes)
              images[i] = ImageFormatReader.read8BitPaletted(fm, width, height, true);
            }
            else {
              //System.out.println("image " + i);

              // a delta for an animation
              int dataLength = 0;
              if (i == numImages - 1) {
                // the last delta
                dataLength = (int) arcSize - imageOffsets[i];
              }
              else {
                // a delta in the middle
                dataLength = imageOffsets[i + 1] - imageOffsets[i];
              }

              // the data is a delta, so start with the previous image
              int[] previousPixels = images[i - 1].getPixels();
              int numPixels = previousPixels.length;

              int[] pixels = new int[numPixels];
              System.arraycopy(previousPixels, 0, pixels, 0, numPixels);

              int outPos = 0;
              while (outPos < numPixels && dataLength > 0) {
                // 1 - Code
                int code = ByteConverter.unsign(fm.readByte());
                dataLength--;

                //System.out.println("  " + code + " at " + (fm.getOffset() - 1) + " going to outpos " + outPos);

                if (code == 0) {
                  // 1 - Repeat Count
                  int repeat = ByteConverter.unsign(fm.readByte());
                  dataLength--;

                  // 1 - Palette Index to repeat X times
                  int pixel = ByteConverter.unsign(fm.readByte());
                  dataLength--;

                  for (int c = 0; c < repeat; c++) {
                    pixels[outPos] = pixel;
                    outPos++;
                  }
                }
                else if (code == 128) {
                  // 2 - Parameter (unsigned)
                  int param = ShortConverter.unsign(fm.readShort());
                  dataLength -= 2;

                  if (param == 0) {
                    break; // end of animation
                  }
                  else if ((param & 0xC000) == 0xC000) {
                    // Repeat Count = (Parameter & 0x3FFF)
                    int repeat = param & 0x3FFF;
                    // 1 - Palette Index to repeat X times
                    int pixel = ByteConverter.unsign(fm.readByte());
                    dataLength--;

                    for (int c = 0; c < repeat; c++) {
                      pixels[outPos] = pixel;
                      outPos++;
                    }
                  }
                  else {
                    // skip Parameter pixels
                    outPos += param;
                  }
                }
                else if (code > 128) {
                  if (code == 0xFF) {
                    int currentPos = (int) fm.getOffset();
                    int nextByte = ByteConverter.unsign(fm.readByte());

                    if (nextByte > 0x80) {
                      dataLength--;

                      // skip nextByte-1 pixels
                      outPos += nextByte - 1;
                    }
                    else {
                      fm.relativeSeek(currentPos);
                      // skip code-128 pixels
                      outPos += code - 128;
                    }
                  }
                  else {
                    // skip code-128 pixels
                    outPos += code - 128;
                  }
                }
                else {
                  // Copy the next X palette Indexes

                  for (int c = 0; c < code; c++) {
                    //if (outPos == numPixels) {
                    //  break;
                    //}
                    pixels[outPos] = ByteConverter.unsign(fm.readByte());
                    outPos++;
                    dataLength--;
                  }

                }
              }

              images[i] = new PalettedImageResource(pixels, width, height, PaletteManager.getCurrentPalette().getPalette());

            }
          }

          // rotate and flip the images, and set frame transitions
          for (int i = 0; i < numImages; i++) {
            ImageResource image = images[i];
            image = ImageFormatReader.rotateLeft(image);
            image = ImageFormatReader.flipVertically(image);
            images[i] = image;

            if (i == 0) {
              image.setPreviousFrame(images[numImages - 1]);
              image.setNextFrame(images[i + 1]);
            }
            else if (i == numImages - 1) {
              image.setPreviousFrame(images[i - 1]);
              image.setNextFrame(images[0]);
            }
            else {
              image.setPreviousFrame(images[i - 1]);
              image.setNextFrame(images[i + 1]);
            }
          }

          imageResource = images[0];
          imageResource.setManualFrameTransition(false); // it's an animation
          imageResource.setAnimationSpeed(speed);
        }
        else {
          // lots of related images

          int numImages = 256; // guess max
          int realNumImages = 0;
          fm.relativeSeek(32);

          ImageResource[] images = new ImageResource[numImages];
          while (fm.getOffset() < arcSize) {
            // 2 - Image Format (16/17=single image, 19=multiple images)
            fm.skip(2);

            // 2 - Image Width
            width = fm.readShort();
            FieldValidator.checkWidth(width);

            // 2 - Image Height
            height = fm.readShort();
            FieldValidator.checkHeight(height);

            // X - Image Data (palette indexes)
            images[realNumImages] = ImageFormatReader.read8BitPaletted(fm, width, height, true);
            realNumImages++;

            int padding = ArchivePlugin.calculatePadding(((width * height) + 6), 16);
            fm.skip(padding);
          }
          numImages = realNumImages;

          for (int i = 0; i < numImages; i++) {
            ImageResource image = images[i];

            image = ImageFormatReader.rotateLeft(image);
            image = ImageFormatReader.flipVertically(image);
            images[i] = image;

            if (i == 0) {
              image.setPreviousFrame(images[numImages - 1]);
              image.setNextFrame(images[i + 1]);
            }
            else if (i == numImages - 1) {
              image.setPreviousFrame(images[i - 1]);
              image.setNextFrame(images[0]);
            }
            else {
              image.setPreviousFrame(images[i - 1]);
              image.setNextFrame(images[i + 1]);
            }
          }

          imageResource = images[0];
          imageResource.setManualFrameTransition(true); // it's not an animation, just a collection of related images
        }

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