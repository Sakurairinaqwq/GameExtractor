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
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;
import org.watto.io.converter.ByteConverter;
import org.watto.io.converter.ShortConverter;

/**
**********************************************************************************************
Ref: https://www.compuphase.com/flic.htm
**********************************************************************************************
**/
public class Viewer_FLC extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_FLC() {
    super("FLC", "FLC (Flic) Image");
    setExtensions("flc");

    setGames("Civilization 3",
        "Autodesk Animator Pro");
    setPlatforms("PC");
    setStandardFileFormat(true);
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
      if (!(plugin instanceof AllFilesPlugin)) {
        return 0;
      }

      if (FieldValidator.checkExtension(fm, extensions)) {
        rating += 25;
      }
      else {
        return 0;
      }

      // 4 - File Length
      if (fm.readInt() == fm.getLength()) {
        rating += 5;
      }

      if (fm.readShort() == -20718) {
        rating += 5;
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

      long arcSize = fm.getLength();

      // 4 - File Length
      // 2 - Type (-20718 = standard FLC)
      fm.skip(6);

      // 2 - Number of Frames
      int numFrames = ShortConverter.unsign(fm.readShort()) + 10; // add 10 (found to need a few extra frames during testing), and resize later if needed

      // 2 - Image Width
      int width = ShortConverter.unsign(fm.readShort());
      FieldValidator.checkWidth(width);

      // 2 - Image Height
      int height = ShortConverter.unsign(fm.readShort());
      FieldValidator.checkHeight(height);

      // 2 - Bits Per Pixel (8)
      // 2 - Flags
      fm.skip(4);

      // 4 - Speed (Delay between frames)
      int speed = fm.readInt();
      if (speed == 0) {
        speed = 20;
      }
      FieldValidator.checkRange(speed, 1, 5000);

      // 2 - null
      // 4 - Creation Date
      // 4 - Creator ID
      // 4 - Modified Date
      // 4 - Modified ID
      // 2 - Square Width
      // 2 - SSquare Height
      // 2 - EGI Flags
      // 2 - EGI Key Frames
      // 2 - EGI Total Frames
      // 4 - EGI Maximum Chunk Size
      // 2 - EGI Maximum Number of Regions
      // 2 - EGI Number of Transparent Levels
      // 24 - null
      fm.relativeSeek(80);

      // 4 - Frame 1 Offset
      int frameOffset = fm.readInt();
      FieldValidator.checkOffset(frameOffset, arcSize);

      // 4 - Frame 2 Offset
      // 40 - null
      fm.relativeSeek(frameOffset);

      ImageResource[] frames = new ImageResource[numFrames];

      int numPixels = width * height;

      int currentFrame = 0;
      int[] previousPixels = null;
      int[] palette = null;

      while (fm.getOffset() < arcSize) {
        //System.out.println("Chunk start at " + fm.getOffset());
        long nextOffset = fm.getOffset();

        // 4 - Chunk Length (including these header fields and all sub-chunks)
        int chunkLength = fm.readInt();
        FieldValidator.checkLength(chunkLength, arcSize);

        nextOffset += chunkLength;

        // 2 - Chunk Type (-3590 = frame chunk)
        short chunkType = fm.readShort();

        if (chunkType != -3590) {
          // skip any other chunk types
          fm.skip(chunkLength - 6); // -6 because we've already read 6 bytes above
        }
        else {
          // a frame chunk

          // 2 - Number of Sub-Chunks
          int numSubChunks = ShortConverter.unsign(fm.readShort());

          // 8 - null
          fm.skip(8);

          //System.out.println("  Found Frame number " + currentFrame + " with " + numSubChunks + " subchunks");

          // Sometimes the palette is stored first, other times it's stored second.
          // Want to grab the palette first.
          if (numSubChunks != 1) {
            long currentOffset = fm.getOffset();

            // see if any of the sub-chunks is a palette
            for (int i = 0; i < numSubChunks; i++) {
              // 4 - Chunk Length
              chunkLength = fm.readInt();

              // 2 - Chunk Type (4 = Color Palette, 15=RLE-encoded pixels, 7=Delta RLE-encoded Shorts)
              chunkType = fm.readShort();

              if (chunkType != 4) { // length is sometimes invalid for palettes (like, all byte 205, things like that)
                FieldValidator.checkLength(chunkLength, arcSize);
              }

              if (chunkType == 4) {
                // found a palette (256 color)
                if (i == 0) {
                  // don't need to do anything special, this already comes before the image data, so just go back and read normally
                  break;
                }
                else {
                  // read the palette now, then return

                  // 2 - Number of Packets (1)
                  // 2 - null
                  fm.skip(4);

                  palette = ImageFormatReader.readPaletteRGB(fm, 256);

                  break;
                }
              }
              else {
                // some other chunk, skip it
                fm.skip(chunkLength - 6);
              }
            }

            // Hopefully we have the palette loaded now, so go back to where we were and read normally.
            fm.relativeSeek(currentOffset);

          }

          for (int i = 0; i < numSubChunks; i++) {
            //System.out.println(fm.getOffset());
            long nextChunkOffset = fm.getOffset();

            // 4 - Chunk Length
            chunkLength = fm.readInt();

            // 2 - Chunk Type (4 = Color Palette, 15=RLE-encoded pixels, 7=Delta RLE-encoded Shorts)
            chunkType = fm.readShort();

            if (chunkType != 4) { // length is sometimes invalid for palettes (like, all byte 205, things like that)
              FieldValidator.checkLength(chunkLength, arcSize);
              nextChunkOffset += chunkLength;
            }

            if (chunkType == 4) {
              // Color Palette
              //System.out.println("    Found a Palette");

              // 2 - Number of Packets (1)
              // 2 - null
              fm.skip(4);

              palette = ImageFormatReader.readPaletteRGB(fm, 256);
            }
            else if (chunkType == 15) {
              // RLE-encoded pixels
              //System.out.println("    Found an RLE at " + fm.getOffset());

              if (palette == null) {
                ErrorLogger.log("[Viewer_FLC] Reading pixels without a palette");
                //palette = PaletteGenerator.getGrayscale();
                return null;
              }

              int numLines = height;

              int[] pixels = new int[numPixels];
              int outPos = 0;
              for (int h = 0; h < numLines; h++) {
                //System.out.println("      Line " + h + " at " + fm.getOffset());

                //if (outPos != h * width) {
                //  System.out.println("        OutPos Mismatch: " + outPos + " vs " + (h * width));
                //}

                // 1 - Packet Count (ignore)
                fm.skip(1);

                int lineBytesCreated = 0;
                while (lineBytesCreated < width) {
                  // 1 - Count
                  int count = fm.readByte();

                  if (count < 0) {
                    // X - Read the next X bytes
                    //count = ByteConverter.unsign((byte) count);
                    count = 0 - count;

                    for (int c = 0; c < count; c++) {
                      int pixel = palette[ByteConverter.unsign(fm.readByte())];
                      pixels[outPos] = pixel;
                      outPos++;
                    }

                    lineBytesCreated += count;
                  }
                  else {
                    // 1 - Pixel (repeat this pixel "Count" times)
                    int pixel = palette[ByteConverter.unsign(fm.readByte())];

                    for (int c = 0; c < count; c++) {
                      pixels[outPos] = pixel;
                      outPos++;
                    }

                    lineBytesCreated += count;
                  }

                }
              }

              // Now we have a frame, store it.
              ImageResource imageResource = new ImageResource(pixels, width, height);
              frames[currentFrame] = imageResource;
              currentFrame++;

              previousPixels = pixels; // so we can use them in the next frame

            }
            else if (chunkType == 16) {
              // Raw Pixels
              //System.out.println("    Found RAW data at " + fm.getOffset());

              if (palette == null) {
                ErrorLogger.log("[Viewer_FLC] Reading pixels without a palette");
                //palette = PaletteGenerator.getGrayscale();
                return null;
              }

              int[] pixels = new int[numPixels];
              for (int p = 0; p < numPixels; p++) {
                pixels[p] = palette[ByteConverter.unsign(fm.readByte())];
              }

              // Now we have a frame, store it.
              ImageResource imageResource = new ImageResource(pixels, width, height);
              frames[currentFrame] = imageResource;
              currentFrame++;

              previousPixels = pixels; // so we can use them in the next frame

            }
            else if (chunkType == 7) {
              // Delta RLE-encoded Shorts
              //System.out.println("    Found a Delta at " + fm.getOffset());

              if (palette == null) {
                ErrorLogger.log("[Viewer_FLC] Reading pixels without a palette");
                //palette = PaletteGenerator.getGrayscale();
                return null;
              }

              // get the previous frame
              int[] pixels = new int[numPixels];
              System.arraycopy(previousPixels, 0, pixels, 0, numPixels);

              // 2 - Number of Lines (not including skipped lines)
              int numLines = ShortConverter.unsign(fm.readShort());

              int linesProcessed = 0;
              int outPos = 0;
              while (numLines > 0) {
                //ErrorLogger.log("      Read OpCode at " + fm.getOffset());

                // 2 - Op Code
                int opCode = ShortConverter.unsign(fm.readShort());
                int opCodeFlags = opCode >> 14;

                if (opCodeFlags == 1) {
                  ErrorLogger.log("[Viewer_FLC] Invalid OpCode");
                  return null;
                }
                else if (opCodeFlags == 2) {
                  ErrorLogger.log("[Viewer_FLC] Unsupported OpCode");
                  return null;
                }
                else if (opCodeFlags == 3) {
                  // Line Skip Count

                  //int skippedLines = opCode & 16383;
                  int skippedLines = 0 - (short) opCode;

                  //ErrorLogger.log("        Skipping lines: " + skippedLines);

                  linesProcessed += skippedLines;
                }
                else if (opCodeFlags == 0) {
                  int packetCount = opCode;

                  // work out where the line starts in the output image
                  outPos = linesProcessed * width;

                  // now read the packets
                  for (int p = 0; p < packetCount; p++) {
                    //ErrorLogger.log("        Reading line " + p + " of " + packetCount + " at " + fm.getOffset());

                    // 1 - Pixel Skip Count
                    int skipCount = ByteConverter.unsign(fm.readByte());
                    outPos += skipCount;

                    // 1 - Count
                    int count = fm.readByte();
                    //ErrorLogger.log("          skip count = " + skipCount + ", count = " + count);

                    if (count >= 0) {
                      // X - Read the next X*2 bytes
                      count *= 2;

                      for (int c = 0; c < count; c++) {
                        int pixel = palette[ByteConverter.unsign(fm.readByte())];
                        pixels[outPos] = pixel;
                        outPos++;

                      }
                    }
                    else {
                      // 2 - Pixel (repeat these pixels "Count" times)
                      //count = ByteConverter.unsign((byte) count);
                      count = 0 - count;

                      int pixel1 = palette[ByteConverter.unsign(fm.readByte())];
                      int pixel2 = palette[ByteConverter.unsign(fm.readByte())];

                      for (int c = 0; c < count; c++) {
                        pixels[outPos] = pixel1;
                        outPos++;
                        pixels[outPos] = pixel2;
                        outPos++;
                      }

                    }

                  }

                  // we've finished reading a line
                  numLines--;
                  linesProcessed++;

                }

              }

              // Now we have a frame, store it.
              ImageResource imageResource = new ImageResource(pixels, width, height);
              frames[currentFrame] = imageResource;
              currentFrame++;

              previousPixels = pixels; // so we can use them in the next frame

            }
            else {
              //ErrorLogger.log("[Viewer_FLC] Unsupported chunk type: " + chunkType);
              fm.skip(chunkLength - 6);
            }

            if (chunkType != 4) {
              fm.relativeSeek(nextChunkOffset); // just in case, because some chunks are padded by a few bytes
            }
          }

        }

        fm.relativeSeek(nextOffset); // just in case, because some chunks are padded by a few bytes

      }

      fm.close();

      if (currentFrame < numFrames)

      {
        numFrames = currentFrame; // we added an extra frame "just in case", but for some reason we didn't need it.
      }

      // set the frame transitions
      for (int i = 0; i < numFrames; i++) {
        ImageResource frame = frames[i];
        if (i == 0) {
          frame.setNextFrame(frames[i + 1]);
          frame.setPreviousFrame(frames[numFrames - 1]);
          frame.setAnimationSpeed(speed);
        }
        else if (i == numFrames - 1) {
          frame.setNextFrame(frames[0]);
          frame.setPreviousFrame(frames[i - 1]);
          frame.setAnimationSpeed(speed);
        }
        else {
          frame.setNextFrame(frames[i + 1]);
          frame.setPreviousFrame(frames[i - 1]);
          frame.setAnimationSpeed(speed);
        }
      }

      ImageResource firstFrame = frames[0];
      firstFrame.setManualFrameTransition(false); // it's an animation

      PreviewPanel_Image preview = new PreviewPanel_Image(firstFrame);

      return preview;

    }
    catch (

    Throwable t) {
      logError(t);
      return null;
    }
  }

  /**
  **********************************************************************************************
  Reads a resource from the FileManipulator, and generates a Thumbnail for it (generally, only
  an Image ViewerPlugin will do this, but others can do it if they want). The FileManipulator is
  an extracted temp file, not the original archive!
  
  THIS ONE IS SPECIAL - LOADS ONLY THE FIRST FRAME, AND DOES SPECIAL HANDLING FOR PALETTE COMING
  AFTER THE IMAGE DATA.
  **********************************************************************************************
  **/

  @Override
  public ImageResource readThumbnail(FileManipulator fm) {
    try {

      long arcSize = fm.getLength();

      // 4 - File Length
      // 2 - Type (-20718 = standard FLC)
      // 2 - Number of Frames
      fm.skip(8);

      // 2 - Image Width
      int width = ShortConverter.unsign(fm.readShort());
      FieldValidator.checkWidth(width);

      // 2 - Image Height
      int height = ShortConverter.unsign(fm.readShort());
      FieldValidator.checkHeight(height);

      // 2 - Bits Per Pixel (8)
      // 2 - Flags
      // 4 - Speed (Delay between frames)
      // 2 - null
      // 4 - Creation Date
      // 4 - Creator ID
      // 4 - Modified Date
      // 4 - Modified ID
      // 2 - Square Width
      // 2 - SSquare Height
      // 2 - EGI Flags
      // 2 - EGI Key Frames
      // 2 - EGI Total Frames
      // 4 - EGI Maximum Chunk Size
      // 2 - EGI Maximum Number of Regions
      // 2 - EGI Number of Transparent Levels
      // 24 - null
      fm.skip(68);

      // 4 - Frame 1 Offset
      int frameOffset = fm.readInt();
      FieldValidator.checkOffset(frameOffset, arcSize);

      // 4 - Frame 2 Offset
      // 40 - null
      fm.relativeSeek(frameOffset);

      int numPixels = width * height;

      int[] palette = null;
      byte[] imageDataBytes = null;
      int pixelFormat = 0;

      while (fm.getOffset() < arcSize) {
        if (palette != null && imageDataBytes != null) {
          break;
        }

        //System.out.println("Chunk start at " + fm.getOffset());

        // 4 - Chunk Length (including these header fields and all sub-chunks)
        int chunkLength = fm.readInt();
        FieldValidator.checkLength(chunkLength, arcSize);

        // 2 - Chunk Type (-3590 = frame chunk)
        short chunkType = fm.readShort();

        if (chunkType != -3590) {
          // skip any other chunk types
          fm.skip(chunkLength - 6); // -6 because we've already read 6 bytes above
        }
        else {
          // a frame chunk

          // 2 - Number of Sub-Chunks
          int numSubChunks = ShortConverter.unsign(fm.readShort());

          // 8 - null
          fm.skip(8);

          //System.out.println("  Found Frame number " + currentFrame + " with " + numSubChunks + " subchunks");

          for (int i = 0; i < numSubChunks; i++) {
            // 4 - Chunk Length
            chunkLength = fm.readInt();
            if (chunkLength != -842150451) { // padding (all byte 205's)
              FieldValidator.checkLength(chunkLength, arcSize);
            }

            // 2 - Chunk Type (4 = Color Palette, 15=RLE-encoded pixels, 7=Delta RLE-encoded Shorts)
            chunkType = fm.readShort();

            if (chunkType == 4) {
              // Color Palette
              //System.out.println("    Found a Palette");

              if (chunkLength == -842150451) {
                chunkLength = 778;
              }

              // 2 - Number of Packets (1)
              // 2 - null
              fm.skip(4);

              chunkLength -= 10;

              if (chunkLength == 768) {
                palette = ImageFormatReader.readPaletteRGB(fm, 256);
              }
              else {
                ErrorLogger.log("[Viewer_FLC] Unsupported palette length: " + chunkLength);
                return null;
              }
            }
            else if (chunkType == 15 || chunkType == 16) {
              // RLE-encoded pixels
              //System.out.println("    Found an RLE at " + fm.getOffset());
              pixelFormat = chunkType;

              // want to grab and store the bytes for the image data, then load that in After the palette is loaded.
              imageDataBytes = fm.readBytes(chunkLength - 6);
              continue;
            }
            else {
              // anything else
              fm.skip(chunkLength - 6);
            }
          }
        }
      }

      // now read the pixels
      fm.close();

      fm = new FileManipulator(new ByteBuffer(imageDataBytes));

      int[] pixels = new int[numPixels];

      if (pixelFormat == 15) {
        // RLE-encoded pixels

        int numLines = height;

        int outPos = 0;
        for (int h = 0; h < numLines; h++) {
          //System.out.println("      Line " + h + " at " + fm.getOffset());

          //if (outPos != h * width) {
          //  System.out.println("        OutPos Mismatch: " + outPos + " vs " + (h * width));
          //}

          // 1 - Packet Count (ignore)
          fm.skip(1);

          int lineBytesCreated = 0;
          while (lineBytesCreated < width) {
            // 1 - Count
            int count = fm.readByte();

            if (count < 0) {
              // X - Read the next X bytes
              //count = ByteConverter.unsign((byte) count);
              count = 0 - count;

              for (int c = 0; c < count; c++) {
                int pixel = palette[ByteConverter.unsign(fm.readByte())];
                pixels[outPos] = pixel;
                outPos++;
              }

              lineBytesCreated += count;
            }
            else {
              // 1 - Pixel (repeat this pixel "Count" times)
              int pixel = palette[ByteConverter.unsign(fm.readByte())];

              for (int c = 0; c < count; c++) {
                pixels[outPos] = pixel;
                outPos++;
              }

              lineBytesCreated += count;
            }

          }
        }
      }
      else if (pixelFormat == 16) {
        // Raw Pixels

        for (int p = 0; p < numPixels; p++) {
          pixels[p] = palette[ByteConverter.unsign(fm.readByte())];
        }

      }

      // Now we have a frame, store it.
      ImageResource imageResource = new ImageResource(pixels, width, height);

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