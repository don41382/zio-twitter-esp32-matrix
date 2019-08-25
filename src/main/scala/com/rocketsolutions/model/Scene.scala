package com.rocketsolutions.model
import java.awt.Color
import java.awt.image.BufferedImage
import java.net.URL

import zio.duration._

case class Frame(id: Short, delay: Duration, img565: BufferedImage)

sealed trait PlaySchedule
case object Once extends PlaySchedule
case class Repeat(count: Int) extends PlaySchedule

object PlaySchedule {
  def toCount(playSchedule: PlaySchedule) = playSchedule match {
    case Once => 1
    case Repeat(count) => count
  }
}

sealed trait ClipDuration
case object Length extends ClipDuration
case object Fit extends ClipDuration
case class Timed(time: Duration) extends ClipDuration

case class Scene(schedule: ClipDuration, frames: Seq[Frame])

sealed trait Clip {
  val play: PlaySchedule
  val duration: ClipDuration
}

case object Pause extends Clip {
  override  val play: PlaySchedule = Once
  override  val duration: ClipDuration = Timed(2 second)
}
case class Solid(color: Color, override val duration: ClipDuration, override val play: PlaySchedule = Once) extends Clip
case class GifPlay(name: String, override val duration: ClipDuration = Length, override val play: PlaySchedule = Once) extends Clip
case class TwoHeader(title: String, sub: String, override val duration: ClipDuration, override val play: PlaySchedule = Once) extends Clip
case class WebImage(url: URL, override val duration: ClipDuration = Length, override val play: PlaySchedule = Once) extends Clip

case class Dimension(width: Int, height: Int)
case class FullScene(dim: Dimension, fg: Seq[Clip], bg: Seq[Clip])

case class ClipWithFinalTime(clip: Clip, time: Duration)
case class ClipWithTime(clip: Clip, time: Option[Duration])

object Scene {
  val transparent = new Color(0,0,0,0)
}