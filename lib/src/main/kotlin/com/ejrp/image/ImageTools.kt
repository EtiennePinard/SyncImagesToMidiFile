package com.ejrp.image

import java.awt.Image
import java.awt.image.BufferedImage

/**
 * Converts the buffered image to a specific type
 * @param targetType The type that the buffered needs to be converted to
 * @return A new buffered image of the appropriate type or the same buffered image if it already was the target type
 */
fun BufferedImage.convertToType(targetType: Int): BufferedImage {
    if (this.type == targetType) { return this }
    val image = BufferedImage(this.width, this.height, targetType)
    val graphics = image.createGraphics()
    graphics.drawImage(this, 0, 0, null)
    graphics.dispose()
    return image
}

/**
 * Resizes a buffered image to a specific width and height
 * @param newWidth The width to resize the buffered image to
 * @param newHeight The height to resize the buffered image to
 * @return A new buffered image with the appropriate width and height or the same buffered image if it already was the new size
 */
fun BufferedImage.resize(newWidth: Int, newHeight: Int): BufferedImage {
    if (this.width == newWidth && this.height == newHeight) { return this }
    val tmp = this.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
    val dimg = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
    val g2d = dimg.createGraphics()
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    return dimg
}