package com.rocketsolutions
import java.net.InetSocketAddress

import com.rocketsolutions.model.IncomingTweet
import com.rocketsolutions.render.Display
import com.rocketsolutions.twitter.Twitter
import zio._
import zio.duration._

object TwitterDisplay extends zio.App {

  val addr  = new InetSocketAddress("192.168.103.9", 41382)

  def main: ZIO[Environment, Throwable, Unit] = for {
    q <- Queue.bounded[IncomingTweet](10)
    _ <- Twitter.incomingTweets(q,"@RiskIdent").fork
    _ <- Display.displayTweet(addr,q)
      .retry[Environment, Throwable, Duration](Schedule.linear(2 seconds))
  } yield ()

  override def run(
    args: List[String]
  ): ZIO[TwitterDisplay.Environment, Nothing, Int] =
    main.orDie.flatMap(_ => ZIO.succeed(0))
}