package com.rocketsolutions.render
import java.awt.image.BufferedImage
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

import com.rocketsolutions.model._
import com.rocketsolutions.network.TransmitterTCP
import zio.blocking.Blocking
import zio._
import zio.duration._

object Display {

  val dim = Dimension(64,32)

  val waitingTweets = FullScene(dim,
    fg = Seq(
      Pause,
      TwoHeader("WAIT-","ING", Timed(2 seconds)),
      Pause,
      TwoHeader("TWEET","NOW", Timed(2 seconds))
    ),
    bg = Seq(
      GifPlay("/gifs/explode.gif", Fit)
    )
  )

  def showName(t: IncomingTweet) = {
    val (head,sub) = t.username.splitAt(6)
    FullScene(dim,
      fg = Seq(
        GifPlay("/gifs/eyes.gif"),
        TwoHeader("YOU","ROCK", Timed(2 seconds)),
        TwoHeader(head,sub, Timed(4 seconds))
      ),
      bg = Seq(
        GifPlay("/gifs/love-vision.gif", Fit)
      )
    )
  }

  def send(frames: Seq[BufferedImage])(implicit socket: SocketChannel): ZIO[Blocking, Throwable, Unit] =
    ZIO.foreach(frames.zipWithIndex) { case (f, id) =>
      TransmitterTCP.sendFrame(id, dim, f)
    }.unit


  def displayTweet(addr: InetSocketAddress, q: Queue[IncomingTweet])=
    TransmitterTCP.connect(addr).bracket(s => ZIO.effectTotal(s.close()))(implicit s => for {
        rw <- RendererSupport.renderFullScene(waitingTweets)
        _ <- q.poll.flatMap {
          case Some(t) =>
            ZIO.effectTotal(println(s"${t.username}: ${t.tweet}")) *>
              RendererSupport.renderFullScene(showName(t)) >>= send
          case None =>
            send(rw)
        } forever
      } yield ()
    )


}
