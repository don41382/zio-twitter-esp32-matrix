package com.rocketsolutions.network

import java.awt.image.BufferedImage
import java.net.{InetSocketAddress, SocketException}
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

import com.rocketsolutions.model.Dimension
import zio._
import zio.duration._
import zio.blocking.Blocking
import zio.clock.Clock

sealed trait ReceiveCommands
case object ButtonPressed extends ReceiveCommands

case class InvalidCmdPressed(invalidCmd: Byte) extends Exception


object TransmitterTCP {

  def connect(address: InetSocketAddress) = blocking.effectBlocking {
    val s = SocketChannel.open()
    s.connect(address)
    s.socket().setTcpNoDelay(true)
    s
  }

  def receiveRemoteCmd(queue: Queue[ReceiveCommands], socket: SocketChannel): ZIO[Blocking, Throwable, Unit] = for {
    data <- blocking.effectBlocking[Byte]{
      val buffer = ByteBuffer.allocate(1)
      socket.read(buffer)
      buffer.get(0)
    }
    cmd <- data match {
      case 100   => ZIO.succeed(ButtonPressed)
      case other => ZIO.fail(InvalidCmdPressed(other))
    }
    _ <- queue.offer(cmd)
  } yield (Unit)

  def sendFrame(id: Int, dim: Dimension, i: BufferedImage)
    (implicit socket: SocketChannel): ZIO[Blocking, Throwable, Unit] =
    blocking.effectBlocking {
      val buffer = ByteBuffer.wrap(TransmitterTCP.frameToBinary(id, dim, i))
      while (buffer.hasRemaining) {
        socket.write(buffer)
      }
  }

  def frameToBinary(id: Int, dim: Dimension, i: BufferedImage): Array[Byte] = {
    val header = Seq(
      id,
      0,
      dim.width,
      dim.height)
      .map(_.toShort)
      .map(ByteHelper.shortToByte)
      .flatten
      .toArray

    header ++ imageToBytes(ByteHelper.colorTo565Byte, i)
  }

  def imageToBytes(colorToByte: Int => Array[Byte], img: BufferedImage) = {
    (for {
      x <- (0 to img.getWidth - 1)
      y <- (0 to img.getHeight - 1)
    } yield
      (colorToByte(img.getRGB(x, y)).toSeq)).flatten
  }

}