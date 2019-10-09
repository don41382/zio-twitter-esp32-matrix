package com.rocketsolutions.render
import java.awt.image.BufferedImage
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

import com.rocketsolutions.TwitterDisplay.TwitterEnv
import com.rocketsolutions.model._
import com.rocketsolutions.network._
import zio._
import zio.console._
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
      GifPlay("/gifs/pig.gif", Fit)
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


  def send(frames: Seq[BufferedImage])(implicit socket: SocketChannel)=
    ZIO.foreach(frames.zipWithIndex) { case (f, id) =>
      TransmitterTCP.sendFrame(id, dim, f)
    }.unit

  def displayTweet(address: InetSocketAddress, q: Queue[IncomingTweet]):
    ZIO[TwitterEnv, SendError, Unit] =
    TransmitterTCP.connect(address).bracket[TwitterEnv,SendError](s => putStrLn("closing socket") *> ZIO.effectTotal(s.close()))(implicit s => for {
        _      <- putStrLn("display active")
        wscene <- RendererSupport.renderFullScene(waitingTweets)
        _      <- q.poll.flatMap {
          case Some(t) =>
            putStrLn(s"${t.username}: ${t.tweet}") *>
              RendererSupport.renderFullScene(showName(t)) >>= send
          case None =>
            putStrLn("no new tweets for @RiskIdent ...") *>
              send(wscene)
        }.forever
      } yield ())
      .tapError(e => putStrLn(e.errorMsg))
      .retry(Schedule.doWhile((e: SendError) => e match {
        case ConnectionError(_) => true
        case BrokenPipe(_) => true
        case SocketError(_) => true
        case Timeout(_) => true
      }) *> Schedule.linear(2 seconds) && Schedule.recurs(5))

}
