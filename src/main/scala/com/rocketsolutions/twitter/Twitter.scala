package com.rocketsolutions.twitter
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.rocketsolutions.model.IncomingTweet
import zio.clock.Clock
import zio.console.{Console, _}
import zio.{Queue, ZIO}

object Twitter {

  def incomingTweets(q: Queue[IncomingTweet], hashTag: String): ZIO[Clock with Console, Throwable, Boolean] =
    ZIO.effect(TwitterStreamingClient()).bracket(tw => putStrLn("want to close") *> ZIO.fromFuture(implicit ec => tw.shutdown()).orDie *> putStrLn("twitter shutdown"))(tw =>
      ZIO.effectAsyncM[Console, Throwable, Boolean](cb =>
        ZIO.fromFuture(implicit ec => tw.filterStatuses(tracks = Seq(hashTag)) {
          case t: Tweet =>
            cb(q.offer(IncomingTweet(
              username = t.user.map(_.screen_name).getOrElse("unknown"),
              tweet = t.text
          )))
        }).onTermination(_ => putStrLn("future is down"))
      )  *> ZIO.never).interruptible

}



