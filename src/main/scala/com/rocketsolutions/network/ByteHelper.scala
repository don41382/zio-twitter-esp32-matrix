package com.rocketsolutions.network
import java.awt.image.BufferedImage

object ByteHelper {

  def colorTo565Byte(c: Int) = {
    val rgb = c
    val blue = rgb & 0xFF
    val green = (rgb >> 8) & 0xFF
    val red = (rgb >> 16) & 0xFF
    val r_565 = red >> 3
    val g_565 = green >> 2
    val b_565 = blue >> 3
    val rgb_565 = (r_565 << 11) | (g_565 << 5) | b_565
    Array[Byte](((rgb_565 >> 8) & 0xFF).toByte, (rgb_565 & 0xFF).toByte)
  }

  def colorTo888Byte(rgb: Int) = {
    val red: Byte = ((rgb >> 16) & 0xFF).toByte
    val green = ((rgb >> 8) & 0xFF).toByte
    val blue = (rgb & 0xFF).toByte
    Array[Byte](red.toByte, green.toByte, blue.toByte)
  }

  def shortToByte(short: Short): Array[Byte] =
    Array[Byte](((short & 0xFF00) >> 8).toByte, ((short & 0x00FF)).toByte)

  def convertImageTo565(i: BufferedImage): BufferedImage = {
    val convertedImg = new BufferedImage(
      i.getWidth,
      i.getHeight,
      BufferedImage.TYPE_USHORT_565_RGB)
    convertedImg.getGraphics.drawImage(i, 0, 0, null)
    convertedImg

  }
}
