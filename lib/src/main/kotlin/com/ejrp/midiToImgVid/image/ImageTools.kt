package com.ejrp.midiToImgVid.image

import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_3BYTE_BGR

/**
 * Converts the buffered image to a specific type
 *
 * Note: Flushed the orignal BufferedImage
 *
 * @param targetType The type that the buffered needs to be converted to
 * @return A new buffered image of the appropriate type or the same buffered image if it already was the target type
 */
fun BufferedImage.convertToType(targetType: Int): BufferedImage {
    if (this.type == targetType) {
        return this
    }
    val image = BufferedImage(this.width, this.height, targetType)
    val graphics = image.createGraphics()
    graphics.drawImage(this, 0, 0, null)
    graphics.dispose()

    // Releasing the memory from the original buffered image to avoid out of memory error
    this.flush()

    return image
}

/**
 * Resizes a buffered image to a specific width and height
 * Warning, if the aspect ratio of the original image is not the same as
 * the aspect ratio of newWidth and newHeight the results will not be very good.
 *
 * @param newWidth The width to resize the buffered image to
 * @param newHeight The height to resize the buffered image to
 * @param warnings Emit a warning if the target aspect ratio is not the same as the current aspect ratio
 * @return A new buffered image with the appropriate width and height or the same buffered image if it already was the new size
 */
fun BufferedImage.resize(newWidth: Int, newHeight: Int, warnings: Boolean = true): BufferedImage {
    if (this.width == newWidth && this.height == newHeight) {
        return this
    }
    val currentAspectRatio = width.toDouble() / height
    val targetAspectRatio = newWidth.toDouble() / newHeight
    if (warnings && currentAspectRatio != targetAspectRatio) {
        println("WARNING: Resizing from aspect ratio $currentAspectRatio to $targetAspectRatio can lead to bad results because the aspect ratios differ")
    }

    val tmp = this.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
    val dimg = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
    val g2d = dimg.createGraphics()
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    return dimg
}

/**
 * Zooms in a buffered image so that it matches a specific width and height,
 * preserving aspect ratio and adding black bars if needed.
 *
 * Note: Flushed the orignal BufferedImage
 *
 * @param newWidth The width to resize the buffered image to
 * @param newHeight The height to resize the buffered image to
 * @return A new buffered image with the appropriate width and height or the same buffered image if it already was the new size
 */
fun BufferedImage.letterboxToSize(newWidth: Int, newHeight: Int): BufferedImage {
    if (this.width == newWidth && this.height == newHeight) {
        return this
    }

    val originalRatio = this.width.toDouble() / this.height
    val targetRatio = newWidth.toDouble() / newHeight

    // Determine scale factor to fit the image inside the target dimensions
    val scaleFactor = if (originalRatio > targetRatio) {
        newWidth.toDouble() / this.width
    } else {
        newHeight.toDouble() / this.height
    }

    val scaledWidth = (this.width * scaleFactor).toInt()
    val scaledHeight = (this.height * scaleFactor).toInt()

    // Center the image
    val x = (newWidth - scaledWidth) / 2
    val y = (newHeight - scaledHeight) / 2

    // Create a new image with black background
    val result = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    val g2d = result.createGraphics()
    g2d.color = Color.BLACK
    g2d.fillRect(0, 0, newWidth, newHeight)

    // Scale the original image
    val scaledInstance: Image = this.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH)
    g2d.drawImage(scaledInstance, x, y, null)
    g2d.dispose()

    // Releasing the memory from the original buffered image to avoid out of memory error
    this.flush()

    return result
}

/**
 * Creates black buffered image with the specified dimensions.
 *
 * @param width The width of the image
 * @param height The height of the image
 * @return A buffered image entirely black with the specified dimensions
 */
fun getBlackBufferedImage(width: Int, height: Int): BufferedImage {
    val result = BufferedImage(width, height, TYPE_3BYTE_BGR)
    val graphics = result.createGraphics()
    graphics.color = Color.BLACK
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()

    return result
}