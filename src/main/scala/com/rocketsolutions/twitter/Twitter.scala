package com.rocketsolutions.twitter
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.rocketsolutions.model.IncomingTweet
import zio.{Queue, ZIO}

object Twitter {

  def incomingTweets(q: Queue[IncomingTweet], hashTag: String): ZIO[Any, Throwable, Boolean] =
    ZIO.effect(TwitterStreamingClient()).bracket(tw => ZIO.effectTotal(tw.shutdown()))(tw =>
      ZIO.effectAsyncM[Any, Throwable, Boolean](cb =>
        ZIO.fromFuture(implicit ec => tw.filterStatuses(tracks = Seq(hashTag)) {
          case t: Tweet =>
            cb(q.offer(IncomingTweet(
              username = t.user.map(_.screen_name).getOrElse("unknown"),
              tweet = t.text
            )))
        }
        )) *> ZIO.never)

}
