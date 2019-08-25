package com.rocketsolutions.render
import java.io.InputStream
import java.util.concurrent.TimeUnit

import com.madgag.gif.fmsware.GifDecoder
import com.rocketsolutions.model.Frame
import com.rocketsolutions.network.ByteHelper
import zio.duration._
import zio.{IO, ZIO}

sealed trait OpenFileError extends Exception
case object FileNotFound   extends OpenFileError

object GifLoader {

  protected def openGif(input: InputStream): IO[OpenFileError, GifDecoder] =
    ZIO.succeed(new GifDecoder()).flatMap { gif =>
      if (input != null && gif.read(input) == GifDecoder.STATUS_OK) {
        ZIO.succeed(gif)
      } else {
        ZIO.fail(FileNotFound)
      }
    }

  def open(name: String): IO[OpenFileError, Seq[Frame]] = ZIO.bracket(ZIO.effect(getClass.getResourceAsStream(name)).orDie)(stream => ZIO.effect(stream.close()).orDie)(stream => for {
    gif <- openGif(stream)
    frames = (0 to gif.getFrameCount - 1).map(
      id =>
        Frame(
          id.toShort,
          Duration.apply(gif.getDelay(id), TimeUnit.MILLISECONDS),
          ByteHelper.convertImageTo565(gif.getFrame(id))
        ))
  } yield (frames))
}
