package com.rocketsolutions.network

import java.awt.image.BufferedImage
import java.io.IOException
import java.net.{InetSocketAddress, SocketException}
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

import akka.stream.ConnectionException
import com.rocketsolutions.TwitterDisplay.TwitterEnv
import com.rocketsolutions.model.Dimension
import zio._
import zio.blocking.Blocking
import zio.duration._

sealed trait ReceiveCommands
case object ButtonPressed extends ReceiveCommands

case class InvalidCmdPressed(invalidCmd: Byte) extends Exception

sealed trait SendError {
  val errorMsg: String
}

case class Timeout(errorMsg: String = "timeout") extends SendError
case class BrokenPipe(errorMsg: String) extends SendError
case class ConnectionError(errorMsg: String) extends SendError
case class SocketError(errorMsg: String) extends SendError


object TransmitterTCP {

  def connect(address: InetSocketAddress): ZIO[TwitterEnv, SendError, SocketChannel] = blocking.effectBlocking {
    val s = SocketChannel.open()
    s.socket().setSoTimeout(1000)
    s.socket().setTcpNoDelay(true)
    s.connect(address)
    s
  }.timeoutFail(new SocketException(s"timeout - no connection to $address"))(2 seconds).refineOrDie {
    case e: ConnectionException =>
      ConnectionError(e.getMessage)
    case e: SocketException =>
      SocketError(e.getMessage)
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
    (implicit socket: SocketChannel): ZIO[TwitterEnv, SendError, Unit] = blocking.effectBlocking {
      val buffer = ByteBuffer.wrap(TransmitterTCP.frameToBinary(id, dim, i))
      while (buffer.hasRemaining) {
        socket.write(buffer)
      }
    }.refineOrDie {
    case e: java.io.IOException =>
      BrokenPipe(e.getMessage)
    }.timeoutFail(Timeout("send frame was longer than 2 seconds"))(2 seconds)

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