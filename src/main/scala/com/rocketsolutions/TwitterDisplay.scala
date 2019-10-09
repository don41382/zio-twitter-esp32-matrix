package com.rocketsolutions
import java.net.InetSocketAddress

import com.rocketsolutions.model.IncomingTweet
import com.rocketsolutions.render.Display
import com.rocketsolutions.twitter.Twitter
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console._
import zio.random.Random
import zio.system.System

object TwitterDisplay extends zio.App {
  val addr  = new InetSocketAddress("192.168.103.4", 41382)

  type TwitterEnv = Clock with Console with System with Random with Blocking

  def main: ZIO[TwitterEnv, Throwable, Unit] = for {
    queue   <- Queue.bounded[IncomingTweet](10)
    _       <- putStrLn("Waiting for tweets ...")
    twitter <- Twitter.incomingTweets(queue,"@RiskIdent").fork
    _       <- putStrLn("Starting display ...")
    display <- Display.displayTweet(addr,queue).fork
    _       <- ZIO.never
    _       <- putStrLn("it's the end")
  } yield ()

  override def run(
    args: List[String]
  ): ZIO[TwitterDisplay.Environment, Nothing, Int] =
    main.orDie.flatMap(_ => ZIO.succeed(0))
}