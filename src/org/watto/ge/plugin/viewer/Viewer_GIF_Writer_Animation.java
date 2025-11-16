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

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.watto.Settings;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.ImageResource;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.io.FileManipulator;
import org.watto.io.stream.ManipulatorOutputStream;

/**
**********************************************************************************************
Ref: https://stackoverflow.com/questions/777947/creating-animated-gif-with-imageio
**********************************************************************************************
**/
public class Viewer_GIF_Writer_Animation extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_GIF_Writer_Animation() {
    super("GIF_Writer_Animation", "GIF Animation");
    setExtensions("gif");
    setStandardFileFormat(true);
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public boolean canWrite(PreviewPanel panel) {
    if (panel instanceof PreviewPanel_Image) {
      return true;
    }
    return false;
  }

  /**
  **********************************************************************************************
  Can this plugin write an animation? If so, we want to export a single animation file, not each frame.
  **********************************************************************************************
  **/
  @Override
  public boolean canWriteAnimation() {
    return true;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public int getMatchRating(FileManipulator fm) {
    return 0; // writer only, not a reader
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public PreviewPanel read(File path) {
    return null;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public PreviewPanel read(FileManipulator fm) {
    return read(fm.getFile());
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
    return null;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public void write(PreviewPanel preview, FileManipulator fm) {
    try {

      int imageWidth = -1;
      int imageHeight = -1;

      if (preview instanceof PreviewPanel_Image) {
        PreviewPanel_Image ivp = (PreviewPanel_Image) preview;
        imageWidth = ivp.getImageWidth();
        imageHeight = ivp.getImageHeight();
      }
      else {
        return;
      }

      if (imageWidth == -1 || imageHeight == -1) {
        return;
      }

      PreviewPanel_Image imagePanel = (PreviewPanel_Image) preview;
      ImageResource imageResource = imagePanel.getImageResource();

      // check for an animation
      if (imageResource.getNextFrame() != null) {

        // If we've already moved to show a different frame (ie we're not starting on frame #1), move back to frame 0 for export
        int currentFrame = Settings.getInt("PreviewPanel_Image_CurrentFrame");
        if (currentFrame < 0) {
          currentFrame = 0;
        }
        for (int i = 0; i < currentFrame; i++) {
          imageResource = imageResource.getPreviousFrame();
        }

        ImageResource firstResource = imageResource;

        //
        // Now we have an animation, and we're at the start of it.
        // So, build a writer to be able to generate the file
        //
        Iterator<ImageWriter> iioWriters = ImageIO.getImageWritersBySuffix("gif");
        if (!iioWriters.hasNext()) {
          return; // no GIF writers, for some reason
        }

        ImageWriter gifWriter = iioWriters.next();

        ImageWriteParam imageWriteParam = gifWriter.getDefaultWriteParam();
        ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
        IIOMetadata imageMetaData = gifWriter.getDefaultImageMetadata(imageTypeSpecifier, imageWriteParam);

        String metaFormatName = imageMetaData.getNativeMetadataFormatName();

        IIOMetadataNode root = (IIOMetadataNode) imageMetaData.getAsTree(metaFormatName);

        IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");

        int milliseconds = imageResource.getAnimationSpeed();
        graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("delayTime", Integer.toString(milliseconds / 10));
        graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode commentsNode = getNode(root, "CommentExtensions");
        commentsNode.setAttribute("CommentExtension", "Created by MAH");

        IIOMetadataNode appEntensionsNode = getNode(root, "ApplicationExtensions");

        IIOMetadataNode child = new IIOMetadataNode("ApplicationExtension");

        child.setAttribute("applicationID", "NETSCAPE");
        child.setAttribute("authenticationCode", "2.0");

        int loop = 0; // loop continuously

        child.setUserObject(new byte[] { 0x1, (byte) (loop & 0xFF), (byte) ((loop >> 8) & 0xFF) });
        appEntensionsNode.appendChild(child);

        imageMetaData.setFromTree(metaFormatName, root);

        // Set the file ready for accepting the output
        ImageOutputStream outputStream = new MemoryCacheImageOutputStream(new ManipulatorOutputStream(fm));
        gifWriter.setOutput(outputStream);

        gifWriter.prepareWriteSequence(null);

        //
        // Now start writing out the frames
        //
        for (int i = 0; i < 1000; i++) { // max 1000 frames to export

          BufferedImage bufImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
          Graphics g = bufImage.getGraphics();

          Image image = imageResource.getImage();
          g.drawImage(image, 0, 0, null);

          // write the frame
          gifWriter.writeToSequence(new IIOImage((RenderedImage) bufImage, null, imageMetaData), imageWriteParam);

          // close off the frame (clean up memory)
          g.dispose();

          // prepare for the next frame
          imageResource = imageResource.getNextFrame();

          if (imageResource == firstResource) {
            // back at the beginning
            break;
          }
        }

        //
        // Now close the image
        //
        gifWriter.endWriteSequence();
        outputStream.close();

      }
      else {
        // if it's a single frame, just export using the normal GIF exporter
        new Viewer_GIF_GIF().write(preview, fm);
      }

    }
    catch (Throwable e) {
      logError(e);
    }
  }

  /**
   * Returns an existing child node, or creates and returns a new child node (if 
   * the requested node does not exist).
   * 
   * @param rootNode the <tt>IIOMetadataNode</tt> to search for the child node.
   * @param nodeName the name of the child node.
   * 
   * @return the child node, if found or a new node created with the given name.
   */
  private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
    int nNodes = rootNode.getLength();
    for (int i = 0; i < nNodes; i++) {
      if (rootNode.item(i).getNodeName().compareToIgnoreCase(nodeName) == 0) {
        return ((IIOMetadataNode) rootNode.item(i));
      }
    }
    IIOMetadataNode node = new IIOMetadataNode(nodeName);
    rootNode.appendChild(node);
    return (node);
  }

}